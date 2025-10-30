import 'dart:async';
import 'dart:convert';

import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../core/logging/logger.dart';
import '../../domain/models/session_models.dart';
import '../../services/audio/audio_session_service.dart';
import '../../services/realtime/realtime_api_client.dart';

class WebRtcService {
  WebRtcService({
    required RealtimeApiClient realtimeApiClient,
    required AudioSessionService audioSessionService,
  }) : _realtimeApiClient = realtimeApiClient,
       _audioSessionService = audioSessionService;

  final RealtimeApiClient _realtimeApiClient;
  final AudioSessionService _audioSessionService;

  RTCPeerConnection? _peerConnection;
  RTCDataChannel? _controlChannel;
  MediaStream? _localStream;
  RealtimeSession? _currentSession;
  bool _remotePlaybackActive = false;
  bool? _localMicEnabledBeforePlayback;

  // Track response completion states to delay mic restoration
  bool _responseDoneReceived = false;
  bool _outputBufferClearedReceived = false;

  // Track timestamps for echo detection
  DateTime? _lastPlaybackEndTime;
  bool _waitingForTranscription = false;

  // Fallback timer for microphone restoration
  Timer? _playbackFallbackTimer;

  // Track last assistant response for duplicate detection
  String? _lastAssistantResponse;

  final _metricsController = StreamController<LatencyMetrics>.broadcast();

  Stream<LatencyMetrics> get metricsStream => _metricsController.stream;

  /// Interrupt the current assistant response
  void interruptAssistant() {
    final channel = _controlChannel;
    if (channel == null) return;

    // Send response.cancel first, then conversation.item.truncate to properly interrupt
    // This follows the correct barge-in flow: cancel response -> truncate audio -> wait for truncated event
    channel.send(RTCDataChannelMessage(jsonEncode({'type': 'response.cancel'})));
    channel.send(RTCDataChannelMessage(jsonEncode({'type': 'conversation.item.truncate', 'item_id': 'assistant_response'})));
    logInfo('Interrupted assistant response with proper barge-in flow');
  }

  Future<void> connect(RealtimeSession session) async {
    await disconnect();
    _currentSession = session;

    final iceServers = session.iceServers.isNotEmpty
        ? session.iceServers
              .map(
                (server) => {
                  'urls': server.urls,
                  if (server.username != null) 'username': server.username,
                  if (server.credential != null)
                    'credential': server.credential,
                },
              )
              .toList()
        : [
            {'urls': 'stun:stun.l.google.com:19302'},
          ];

    final configuration = <String, dynamic>{
      'iceServers': iceServers,
      'sdpSemantics': 'unified-plan',
      'bundlePolicy': 'max-bundle',
    };

    _peerConnection = await createPeerConnection(configuration);
    final pc = _peerConnection!;

    pc.onConnectionState = (state) {
      logInfo('WebRTC connection state: $state');
    };
    pc.onIceConnectionState = (state) {
      logInfo('ICE connection state: $state');
    };
    pc.onIceGatheringState = (state) {
      logInfo('ICE gathering state: $state');
    };
    pc.onIceCandidate = (candidate) {
      logInfo('Local ICE candidate gathered');
    };
    pc.onDataChannel = _attachDataChannel;
    pc.onTrack = (event) {
      logInfo('Remote track received');
      if (event.track.kind == 'audio' && event.streams.isNotEmpty) {
        unawaited(_audioSessionService.attachRemoteStream(event.streams.first));
      }
    };

    await _prepareLocalMedia(pc);

    final dataChannel = await pc.createDataChannel(
      'oai-events',
      RTCDataChannelInit()..ordered = true,
    );
    _attachDataChannel(dataChannel);

    final offer = await pc.createOffer({
      'offerToReceiveAudio': true,
      'offerToReceiveVideo': false,
    });
    await pc.setLocalDescription(offer);
    final localSdp = await _waitForCompleteLocalSdp(pc);
    if (localSdp.trim().isEmpty) {
      throw StateError(
        'Local SDP offer is empty; cannot establish realtime session.',
      );
    }

    final answerSdp = await _realtimeApiClient.sendOfferAndGetAnswer(
      sessionId: session.sessionId,
      offerSdp: localSdp,
    );
    await pc.setRemoteDescription(RTCSessionDescription(answerSdp, 'answer'));

    _metricsController.add(
      const LatencyMetrics(asrMs: 100, translationMs: 250, ttsMs: 190),
    );
  }

  Future<void> disconnect() async {
    // Cancel any pending timers
    _playbackFallbackTimer?.cancel();
    _playbackFallbackTimer = null;

    await _controlChannel?.close();
    _controlChannel = null;

    _remotePlaybackActive = false;
    _localMicEnabledBeforePlayback = null;
    _responseDoneReceived = false;
    _outputBufferClearedReceived = false;
    _currentSession = null;

    final localTracks = _localStream?.getTracks() ?? <MediaStreamTrack>[];
    for (final track in localTracks) {
      await track.stop();
    }
    await _localStream?.dispose();
    _localStream = null;

    await _peerConnection?.close();
    _peerConnection = null;

    await _audioSessionService.detachRemoteStream();
  }

  Future<void> _prepareLocalMedia(RTCPeerConnection pc) async {
    _localStream = await navigator.mediaDevices.getUserMedia({
      'audio': {
        'sampleRate': 24000,
        'channelCount': 1,
        'echoCancellation': true,
        'noiseSuppression': true,
        'autoGainControl': true,
      },
      'video': false,
    });

    for (final track in _localStream!.getAudioTracks()) {
      await pc.addTrack(track, _localStream!);
    }
  }

  void _attachDataChannel(RTCDataChannel channel) {
    _controlChannel = channel;
    channel.onMessage = (message) {
      if (message.isBinary) {
        logInfo('WebRTC data message');
        return;
      }
      final text = message.text;
      if (text.isNotEmpty) {
        _handleControlEvent(text);
      }
      logInfo('WebRTC data message', {'binary': false, 'text': text});
    };
    channel.onDataChannelState = (state) {
      logInfo('WebRTC data channel state: $state');
      if (state == RTCDataChannelState.RTCDataChannelOpen) {
        _bootstrapSession();
      }
    };
  }

  void _bootstrapSession() {
    final channel = _controlChannel;
    final session = _currentSession;
    if (channel == null || session == null) return;

    void send(Map<String, dynamic> payload) {
      channel.send(RTCDataChannelMessage(jsonEncode(payload)));
    }

    final turnDetection = session.turnDetectionMode == 'none'
        ? null
        : {
            'type': 'server_vad',
            'threshold': session.turnDetectionThreshold ?? 0.75, // Higher threshold to reduce false triggers from echo
            'silence_duration_ms': session.turnDetectionSilenceMs ?? 700, // Longer silence to prevent premature responses and echo triggering
            'prefix_padding_ms': 300, // Add padding before speech detection to avoid cutting words
            'max_speech_ms': 8000,
          };

    send({
      'type': 'session.update',
      'session': {
        'modalities': ['audio', 'text'],
        'voice': session.voice,
        'turn_detection': turnDetection,
        'output_audio_format': {'type': 'pcm16'},
        'max_response_output_tokens': 160, // Limit output length to reduce truncation issues
        'instructions': '不要寒暄；只有听到明确需求或用户主动提问时再作答。保持简洁，避免重复问候语。', // Prevent repetitive greetings
        'input_audio_transcription': {'model': session.transcriptionModel ?? 'gpt-4o-transcribe-diarize'},
      },
    });
  }

  Future<String> _waitForCompleteLocalSdp(RTCPeerConnection pc) async {
    if (pc.iceGatheringState ==
        RTCIceGatheringState.RTCIceGatheringStateComplete) {
      final localDescription = await pc.getLocalDescription();
      return localDescription?.sdp ?? '';
    }

    final completer = Completer<void>();
    void handleState(RTCIceGatheringState state) {
      if (state == RTCIceGatheringState.RTCIceGatheringStateComplete &&
          !completer.isCompleted) {
        completer.complete();
      }
    }

    final previousHandler = pc.onIceGatheringState;
    pc.onIceGatheringState = (state) {
      previousHandler?.call(state);
      handleState(state);
    };

    await completer.future.timeout(
      const Duration(seconds: 10),
      onTimeout: () => null,
    );
    final localDescription = await pc.getLocalDescription();
    return localDescription?.sdp ?? '';
  }

  void _handleControlEvent(String payload) {
    Map<String, dynamic>? data;
    try {
      final decoded = jsonDecode(payload);
      if (decoded is Map<String, dynamic>) {
        data = decoded;
      }
    } catch (error, stack) {
      logWarning('Failed to decode realtime control message', {
        'payload': payload,
        'error': error.toString(),
        'stackTrace': stack.toString(),
      });
      return;
    }

    if (data == null) return;
    final type = data['type'] as String?;
    if (type == null) return;

    switch (type) {
      case 'output_audio_buffer.started':
        _handleRemotePlaybackStarted();
        break;
      case 'output_audio_buffer.stopped':
        _handleRemotePlaybackStopped();
        break;
      case 'output_audio_buffer.cleared':
        _handleOutputBufferCleared();
        break;
      case 'conversation.item.created':
        _handleConversationItemCreated(data);
        break;
      case 'response.done':
        _handleResponseDone();
        break;
      case 'input_audio_buffer.speech_started':
        _handleSpeechStarted();
        break;
      case 'input_audio_buffer.committed':
        _handleAudioBufferCommitted();
        break;
      case 'conversation.item.audio_transcription.completed':
        _handleAudioTranscriptionCompleted(data);
        break;
      default:
        break;
    }
  }

  void _handleRemotePlaybackStarted() {
    final session = _currentSession;
    if (session == null ||
        !session.muteMicDuringPlayback ||
        _remotePlaybackActive) {
      return;
    }

    // Cancel any pending fallback timer
    _playbackFallbackTimer?.cancel();

    final tracks = _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[];
    if (tracks.isEmpty) return;

    _remotePlaybackActive = true;
    _localMicEnabledBeforePlayback = tracks.first.enabled;
    for (final track in tracks) {
      track.enabled = false;
    }
    logInfo('Muted local microphone during assistant playback', {});
  }

  void _handleRemotePlaybackStopped() {
    _lastPlaybackEndTime = DateTime.now(); // Track when playback ended for echo detection
    // Restore microphone when playback stops, if response is done
    // This handles normal playback completion (not interruption)
    if (_responseDoneReceived && _remotePlaybackActive) {
      logInfo('Playback stopped and response done, restoring microphone', {});
      _restoreMicrophone();
    }
  }

  void _handleOutputBufferCleared() {
    _outputBufferClearedReceived = true;
    _lastPlaybackEndTime = DateTime.now(); // Track when playback ended for echo detection
    // Only restore microphone when both response.done and output_audio_buffer.cleared are received
    // This ensures the assistant's audio has completely finished playing before re-enabling mic
    if (_responseDoneReceived && _remotePlaybackActive) {
      logInfo('Both response.done and output_audio_buffer.cleared received, restoring microphone', {});
      _restoreMicrophone();
    }
  }

  void _handleConversationItemTruncated() {
    // After conversation item is truncated, we can safely start new audio collection
    // This completes the barge-in flow: cancel -> truncate -> wait for truncated -> start new input
    logInfo('Conversation item truncated, barge-in flow completed', {});
    // Note: Microphone restoration will be handled by the cleared event if it comes
  }

  void _handleConversationItemCreated(Map<String, dynamic> data) {
    final item = data['item'] as Map<String, dynamic>?;
    if (item == null) return;

    final role = item['role'] as String?;
    final type = item['type'] as String?;
    final content = item['content'] as List<dynamic>?;

    if (type == 'message' && role == 'user') {
      // Check if transcript is null or too short
      final audioContent = content?.firstWhere(
        (c) => (c as Map<String, dynamic>)['type'] == 'input_audio',
        orElse: () => null,
      ) as Map<String, dynamic>?;

      final transcript = audioContent?['transcript'] as String?;
      if (transcript == null || transcript.trim().length < 3) {
        logInfo('Empty or too short transcript detected, skipping response creation', {
          'transcript': transcript,
        });
        // Send cancel to prevent response
        final channel = _controlChannel;
        if (channel != null) {
          channel.send(RTCDataChannelMessage(jsonEncode({'type': 'response.cancel'})));
        }
        return;
      }
    } else if (type == 'message' && role == 'assistant') {
      // Track assistant response for duplicate detection
      final textContent = content?.firstWhere(
        (c) => (c as Map<String, dynamic>)['type'] == 'text',
        orElse: () => null,
      ) as Map<String, dynamic>?;

      final text = textContent?['text'] as String?;
      if (text != null && text.isNotEmpty) {
        // Normalize text for comparison (remove punctuation, lowercase)
        final normalized = text.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
        if (_lastAssistantResponse != null && _isSimilarResponse(_lastAssistantResponse!, normalized)) {
          logInfo('Duplicate assistant response detected, canceling', {
            'last': _lastAssistantResponse,
            'current': normalized,
          });
          // Cancel this response
          final channel = _controlChannel;
          if (channel != null) {
            channel.send(RTCDataChannelMessage(jsonEncode({'type': 'response.cancel'})));
          }
          return;
        }
        _lastAssistantResponse = normalized;
      }
    }
  }

  void _handleResponseDone() {
    _responseDoneReceived = true;
    // Start fallback timer in case output_audio_buffer.stopped doesn't arrive
    _playbackFallbackTimer?.cancel();
    _playbackFallbackTimer = Timer(const Duration(milliseconds: 600), () {
      if (_responseDoneReceived && _remotePlaybackActive) {
        logInfo('Fallback timer triggered, restoring microphone after response.done', {});
        _restoreMicrophone();
      }
    });
    logInfo('Response done received, starting fallback timer for mic restoration', {});
  }

  void _handleSpeechStarted() {
    // Check if this speech detection might be echo from recent playback
    if (_lastPlaybackEndTime != null) {
      final timeSincePlayback = DateTime.now().difference(_lastPlaybackEndTime!);
      if (timeSincePlayback.inMilliseconds < 500 && _waitingForTranscription) {
        // Likely echo: cancel this input and wait for transcription
        logInfo('Detected potential echo speech, canceling input', {
          'timeSincePlayback': timeSincePlayback.inMilliseconds,
        });
        final channel = _controlChannel;
        if (channel != null) {
          channel.send(RTCDataChannelMessage(jsonEncode({'type': 'response.cancel'})));
        }
        return;
      }
    }
    logInfo('Speech started', {});
  }

  void _handleAudioTranscriptionCompleted(Map<String, dynamic> data) {
    _waitingForTranscription = false;
    final transcript = data['transcript'] as String?;
    logInfo('Audio transcription completed', {'transcript': transcript});
  }

  void _handleAudioBufferCommitted() {
    // When audio buffer is committed, we're waiting for transcription
    _waitingForTranscription = true;
    logInfo('Audio buffer committed, waiting for transcription', {});
  }

  bool _isSimilarResponse(String last, String current) {
    // Simple similarity check: if they share more than 70% of words
    final lastWords = last.split(RegExp(r'\s+')).toSet();
    final currentWords = current.split(RegExp(r'\s+')).toSet();
    final intersection = lastWords.intersection(currentWords);
    final union = lastWords.union(currentWords);
    if (union.isEmpty) return false;
    final similarity = intersection.length / union.length;
    return similarity > 0.7;
  }

  void _restoreMicrophone() {
    // Cancel fallback timer
    _playbackFallbackTimer?.cancel();
    _playbackFallbackTimer = null;

    // Reset states
    _responseDoneReceived = false;
    _outputBufferClearedReceived = false;

    final targetState = _localMicEnabledBeforePlayback;
    _remotePlaybackActive = false;
    _localMicEnabledBeforePlayback = null;

    if (targetState == null) {
      return;
    }

    // Delay microphone restoration to avoid echo triggering VAD
    Future.delayed(const Duration(milliseconds: 250), () {
      final tracks = _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[];
      for (final track in tracks) {
        track.enabled = targetState;
      }
      logInfo('Restored local microphone state after assistant playback (delayed)', {});
    });
  }
}
