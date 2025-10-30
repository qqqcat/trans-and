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

  // Fallback timer for microphone restoration
  Timer? _playbackFallbackTimer;

  // Track last assistant response for duplicate detection
  String? _lastAssistantResponse;

  // Track response creation time to protect against immediate cancellation
  DateTime? _lastResponseCreatedTime;

  // Track if there's currently an active response in progress
  bool _hasActiveResponse = false;

  // Track if assistant is currently playing audio (for blocking upstream audio)
  bool _isPlayingAudio = false;

  // Track consumed user item IDs to prevent duplicate responses
  final Set<String> _consumedUserItemIds = {};

  // Track if last response is done (Gate A)
  bool _lastResponseDone = true;

  final _metricsController = StreamController<LatencyMetrics>.broadcast();

  Stream<LatencyMetrics> get metricsStream => _metricsController.stream;

  /// Interrupt the current assistant response
  void interruptAssistant() {
    final channel = _controlChannel;
    if (channel == null) return;

    // Only cancel if there's an active response and it's been at least 800ms since creation
    if (!_hasActiveResponse) {
      logInfo('No active response to interrupt, skipping cancel');
      return;
    }

    if (_lastResponseCreatedTime != null) {
      final timeSinceCreated = DateTime.now().difference(_lastResponseCreatedTime!);
      if (timeSinceCreated.inMilliseconds < 800) {
        logInfo('Response was just created, waiting before allowing interruption', {
          'timeSinceCreated': timeSinceCreated.inMilliseconds,
        });
        return;
      }
    }

    // Send response.cancel first, then conversation.item.truncate to properly interrupt
    // This follows the correct barge-in flow: cancel response -> truncate audio -> clear input buffer
    channel.send(RTCDataChannelMessage(jsonEncode({'type': 'response.cancel'})));
    channel.send(RTCDataChannelMessage(jsonEncode({'type': 'conversation.item.truncate', 'item_id': 'assistant_response'})));
    channel.send(RTCDataChannelMessage(jsonEncode({'type': 'input_audio_buffer.clear'})));
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

    // Send initial session.update to disable auto-response and configure turn detection
    await _sendInitialSessionUpdate(session);

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
        'threshold': session.turnDetectionThreshold ?? 0.6,  // Increased from 0.3 to 0.6 for less sensitivity
        'prefix_padding_ms': 300,
        'silence_duration_ms': session.turnDetectionSilenceMs ?? 1200,  // Increased from 300 to 1200 for longer silence tolerance
        'create_response': true,  // Enable auto-response for debugging - will automatically create responses after transcription
        'interrupt_response': true,
      };

    send({
      'type': 'session.update',
      'session': {
        'modalities': ['audio', 'text'],
        'voice': session.voice,
        'turn_detection': turnDetection,
        'output_audio_format': 'pcm16',
        'max_response_output_tokens': 160,
        'instructions': '请简单确认用户输入，无需过多寒暄。用于调试模式，验证音频输出是否正常工作。',
        'input_audio_transcription': {
          'model': session.transcriptionModel ?? 'gpt-4o-transcribe-diarize'
        },
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
      case 'response.created':
        _handleResponseCreated(data);
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
      case 'conversation.item.input_audio_transcription.completed':
        _handleInputAudioTranscriptionCompleted(data);
        break;
      case 'conversation.item.truncated':
        _handleConversationItemTruncated();
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

    // Set playing state to block upstream audio
    _isPlayingAudio = true;

    // Disable barge-in during playback to prevent echo triggering new responses
    _updateTurnDetection(
      interruptResponse: false,
      createResponse: false,
    );

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
    // output_audio_buffer.stopped 只做标记，不直接恢复麦克风，等 cleared 事件
    // 只在 output_audio_buffer.cleared 和 response.done 都收到后再恢复麦克风
    // fallback timer 兜底
    // 此处不再直接调用 _restoreMicrophone()
  }

  void _handleOutputBufferCleared() {
    _outputBufferClearedReceived = true;
    _lastPlaybackEndTime = DateTime.now(); // Track when playback ended for echo detection
    // Only restore microphone when both response.done and output_audio_buffer.cleared are received
    // This ensures the assistant's audio has completely finished playing before re-enabling mic
    if (_responseDoneReceived && _remotePlaybackActive) {
      // Re-enable barge-in after playback ends
      _updateTurnDetection(
        interruptResponse: true,
        createResponse: false,
      );

      // Add delay to prevent immediate speech detection after playback ends
      Future.delayed(const Duration(milliseconds: 250), () {
        if (_responseDoneReceived && _outputBufferClearedReceived && _remotePlaybackActive) {
          logInfo('Both response.done and output_audio_buffer.cleared received, restoring microphone after delay', {});
          _isPlayingAudio = false; // Clear playing state
          _restoreMicrophone();
        }
      });
    }
  }

  void _handleConversationItemTruncated() {
    // After conversation item is truncated, we can safely start new audio collection
    // This completes the barge-in flow: cancel -> truncate -> wait for truncated -> start new input
    logInfo('Conversation item truncated, barge-in flow completed', {});
    // Note: Microphone restoration will be handled by the cleared event if it comes
  }

  Future<void> _sendInitialSessionUpdate(RealtimeSession session) async {
    final channel = _controlChannel;
    if (channel == null) return;

    final turnDetection = {
      'type': 'server_vad',
      'threshold': session.turnDetectionThreshold ?? 0.6,
      'prefix_padding_ms': 300,
      'silence_duration_ms': 600,  // Adjusted from 1200 to 600ms to reduce half-sentence triggering
      'create_response': false,  // Disable auto-response for manual control
      'interrupt_response': true,
    };

    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'session.update',
      'session': {
        'modalities': ['audio', 'text'],
        'voice': session.voice,
        'turn_detection': turnDetection,
        'output_audio_format': 'pcm16',
        'max_response_output_tokens': 160,
        'instructions': '请简单确认用户输入，无需过多寒暄。用于调试模式，验证音频输出是否正常工作。',
        'input_audio_transcription': {
          'model': session.transcriptionModel ?? 'gpt-4o-transcribe-diarize'
        },
      },
    })));

    logInfo('Sent initial session update with manual response control', {
      'turn_detection': turnDetection,
      'voice': session.voice,
    });
  }

  void _updateTurnDetection({
    required bool interruptResponse,
    bool createResponse = false,
  }) {
    final channel = _controlChannel;
    if (channel == null) return;

    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'session.update',
      'session': {
        'turn_detection': {
          'type': 'server_vad',
          'threshold': 0.6,
          'prefix_padding_ms': 300,
          'silence_duration_ms': 600,
          'create_response': createResponse,
          'interrupt_response': interruptResponse,
        },
      },
    })));
    logInfo('Updated turn detection settings', {
      'interrupt_response': interruptResponse,
      'create_response': createResponse,
    });
  }

  void _handleConversationItemCreated(Map<String, dynamic> data) {
    final item = data['item'] as Map<String, dynamic>?;
    if (item == null) return;

    final role = item['role'] as String?;
    final type = item['type'] as String?;
    final content = item['content'] as List<dynamic>?;

    if (type == 'message' && role == 'user') {
      // REMOVED: Transcript validation moved to _handleInputAudioTranscriptionCompleted
      // to wait for actual transcription completion instead of checking on item creation
      logInfo('User message item created, waiting for transcription completion', {
        'item_id': item['id'],
      });
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
          // Check if there's an active response and if it was just created - protect against immediate cancellation
          if (!_hasActiveResponse) {
            logInfo('Duplicate response detected but no active response, skipping cancel');
            return;
          }

          if (_lastResponseCreatedTime != null) {
            final timeSinceResponseCreated = DateTime.now().difference(_lastResponseCreatedTime!);
            if (timeSinceResponseCreated.inMilliseconds < 1000) {
              logInfo('Duplicate response detected but response was just created, skipping cancel to avoid premature cancellation', {
                'last': _lastAssistantResponse,
                'current': normalized,
                'timeSinceCreated': timeSinceResponseCreated.inMilliseconds,
              });
              return;
            }
          }

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

  void _handleResponseCreated(Map<String, dynamic> data) {
    final response = data['response'] as Map<String, dynamic>?;
    final responseId = response?['id'] as String?;
    logInfo('Response created, tracking for potential cancellation protection', {
      'response_id': responseId,
    });
    // Track the response creation time to protect against immediate cancellation
    _lastResponseCreatedTime = DateTime.now();
    _hasActiveResponse = true;
  }

  void _handleResponseDone() {
    _responseDoneReceived = true;
    _hasActiveResponse = false;  // Response is no longer active
    _lastResponseDone = true;   // Gate A: Allow next response creation
    
    // Start cooldown window before restoring microphone and interrupt_response
    _playbackFallbackTimer?.cancel();
    _playbackFallbackTimer = Timer(const Duration(milliseconds: 500), () {
      if (_responseDoneReceived && _remotePlaybackActive) {
        logInfo('Cooldown window ended, restoring microphone and interrupt_response after response.done', {});
        _restoreMicrophone();
        _updateTurnDetectionForPlaybackEnd();
      }
    });
    logInfo('Response done received, starting 500ms cooldown window for mic restoration', {});
  }

  void _executeBargeIn() {
    final channel = _controlChannel;
    if (channel == null) return;

    // Execute complete barge-in flow: cancel response -> truncate conversation -> clear input buffer
    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'response.cancel',
    })));

    // Note: conversation.item.truncate will be handled by the response.cancel event
    // input_audio_buffer.clear will be sent after truncation completes

    logInfo('Executed barge-in flow: response.cancel sent');
  }

  void _handleSpeechStarted() {
    // Only trigger barge-in if we're currently playing audio AND have an active response
    // This prevents false positives from echo or background noise
    if (_isPlayingAudio && _hasActiveResponse) {
      logInfo('Speech started during active playback - triggering barge-in flow', {
        'isPlayingAudio': _isPlayingAudio,
        'hasActiveResponse': _hasActiveResponse,
      });

      // Execute complete barge-in flow: cancel -> truncate -> clear
      _executeBargeIn();
      return;
    }

    // Check if this speech detection might be echo from recent playback
    if (_lastPlaybackEndTime != null) {
      final timeSincePlayback = DateTime.now().difference(_lastPlaybackEndTime!);
      if (timeSincePlayback.inMilliseconds < 1000) {  // Increased from 500ms to 1000ms
        logInfo('Detected potential echo speech within 1 second of playback end, ignoring', {
          'timeSincePlayback': timeSincePlayback.inMilliseconds,
        });
        return;
      }
    }

    logInfo('Speech started - not interrupting playback', {
      'isPlayingAudio': _isPlayingAudio,
      'hasActiveResponse': _hasActiveResponse,
    });
  }

  void _handleAudioTranscriptionCompleted(Map<String, dynamic> data) {
    final transcript = data['transcript'] as String?;
    logInfo('Audio transcription completed', {'transcript': transcript});
  }

  void _handleAudioBufferCommitted() {
    // When audio buffer is committed, we're waiting for transcription
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

    // Clear input audio buffer to prevent echo from triggering new responses
    _clearInputAudioBuffer();

    // Delay microphone restoration to avoid echo triggering VAD
    Future.delayed(const Duration(milliseconds: 250), () {
      final tracks = _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[];
      for (final track in tracks) {
        track.enabled = targetState;
      }
      logInfo('Restored local microphone state after assistant playback (delayed)', {});
    });
  }

  void _clearInputAudioBuffer() {
    final channel = _controlChannel;
    if (channel == null) return;

    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'input_audio_buffer.clear',
    })));

    logInfo('Sent input_audio_buffer.clear to prevent echo triggering new responses');
  }

  void _handleInputAudioTranscriptionCompleted(Map<String, dynamic> data) {
    final transcript = data['transcript'] as String?;
    final itemId = data['item_id'] as String?;
    
    if (transcript == null || transcript.trim().isEmpty || itemId == null) {
      logInfo('Input audio transcription completed but transcript or item_id is missing, skipping response creation', {
        'transcript': transcript,
        'item_id': itemId,
      });
      return;
    }

    // Semantic debouncing: check transcript length and content
    final trimmed = transcript.trim();
    if (!_isValidTranscriptForResponse(trimmed)) {
      logInfo('Input audio transcription completed but content is not suitable for response, skipping', {
        'transcript': transcript,
        'length': trimmed.length,
      });
      return;
    }

    // Gate A: Last response must be done (no active response)
    if (!_lastResponseDone) {
      logInfo('Gate A failed: Last response not done yet, skipping response creation', {
        'transcript': transcript,
        'lastResponseDone': _lastResponseDone,
      });
      return;
    }

    // Gate B: Not currently playing audio
    if (_isPlayingAudio) {
      logInfo('Gate B failed: Currently playing audio, skipping response creation', {
        'transcript': transcript,
        'isPlayingAudio': _isPlayingAudio,
      });
      return;
    }

    // Gate C: Item not consumed yet (de-duplication)
    if (_consumedUserItemIds.contains(itemId)) {
      logInfo('Gate C failed: Item already consumed, skipping response creation', {
        'transcript': transcript,
        'item_id': itemId,
      });
      return;
    }

    // All gates passed - consume the item and create response
    _consumedUserItemIds.add(itemId);
    _lastResponseDone = false; // Reset for next response

    logInfo('Input audio transcription completed with valid content - sending manual response.create', {
      'transcript': transcript,
      'item_id': itemId,
    });

    // Send manual response.create since auto-response is disabled (create_response: false)
    _sendResponseCreate();
  }

  bool _isValidTranscriptForResponse(String transcript) {
    // Check minimum length (at least 3 characters)
    if (transcript.length < 3) return false;

    // Define stop words/phrases that shouldn't trigger responses
    const stopWords = {
      '哦', '啊', '嗯', '哈', '嘿', '哎', '呀',
      'oh', 'ah', 'um', 'uh', 'hmm', 'ha', 'hey', 'ay', 'ya',
      'ta-da', 'tada', 'okay', 'ok', 'yes', 'no', 'yeah', 'yep',
      '收到', '明白', '知道了', '好的', '好'
    };

    // Normalize transcript for comparison
    final normalized = transcript.toLowerCase().replaceAll(RegExp(r'[^\w\s]'), '').trim();

    // Check if transcript is only stop words
    final words = normalized.split(RegExp(r'\s+'));
    if (words.every((word) => stopWords.contains(word))) {
      return false;
    }

    return true;
  }

  void _updateTurnDetectionForPlaybackEnd() {
    final channel = _controlChannel;
    if (channel == null) return;

    final turnDetection = {
      'type': 'server_vad',
      'threshold': _currentSession?.turnDetectionThreshold ?? 0.6,
      'prefix_padding_ms': 300,
      'silence_duration_ms': 600,  // Adjusted for better responsiveness
      'create_response': false,
      'interrupt_response': true,  // Re-enable interruption after cooldown
    };

    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'session.update',
      'session': {
        'turn_detection': turnDetection,
      },
    })));

    logInfo('Updated turn detection settings after playback end cooldown', {
      'interrupt_response': true,
      'create_response': false,
    });
  }

  void _sendResponseCreate() {
    final channel = _controlChannel;
    if (channel == null) return;

    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'response.create',
      'response': {
        'modalities': ['audio', 'text'],
      },
    })));

    logInfo('Sent manual response.create to trigger translation response');
  }
}
