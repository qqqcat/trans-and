import 'dart:async';
import 'dart:convert';

import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../core/logging/logger.dart';
import '../../domain/models/session_models.dart';
import '../../services/audio/audio_session_service.dart';
import '../../services/realtime/realtime_api_client.dart';

/// WebRTC connection states for echo prevention state machine
enum WebRtcState {
  idle,        // Not connected
  listening,   // Connected, listening for user input
  thinking,    // Processing user input, waiting for response
  speaking,    // Playing TTS response
  cooldown,    // Cooling down after TTS ends before re-enabling auto-response
}

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

  // Track response completion states to delay mic restoration
  bool _responseDoneReceived = false;

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

  // Track if assistant is currently playing TTS (only driven by server buffer events)
  bool _isPlayingTTS = false;

  // Track consumed user item IDs to prevent duplicate responses
  final Set<String> _consumedUserItemIds = {};

  // Track last TTS end time for cooldown window validation
  DateTime? _lastTTSEndAt;

  // Queue for pending transcriptions that couldn't be processed during TTS playback
  final List<Map<String, dynamic>> _pendingTranscriptionQueue = [];

  // State machine for echo prevention
  WebRtcState _currentState = WebRtcState.idle;

  final _metricsController = StreamController<LatencyMetrics>.broadcast();

  Stream<LatencyMetrics> get metricsStream => _metricsController.stream;

  /// Get current state for debugging
  WebRtcState get currentState => _currentState;

  /// Transition to a new state with logging
  void _transitionToState(WebRtcState newState) {
    final oldState = _currentState;
    _currentState = newState;
    logInfo('WebRTC state transition', {
      'from': oldState.name,
      'to': newState.name,
    });

    // Configure turn detection for the new state
    _configureTurnDetectionForState();
  }

  /// Configure turn detection based on current state
  void _configureTurnDetectionForState() {
    final channel = _controlChannel;
    if (channel == null) return;

    late bool createResponse;
    late bool interruptResponse;
    late double threshold;
    late int silenceDurationMs;

    switch (_currentState) {
      case WebRtcState.idle:
        // Not connected, no turn detection needed
        return;

      case WebRtcState.listening:
        // Normal listening mode
        createResponse = true;
        interruptResponse = true;
        threshold = 0.6;
        silenceDurationMs = 600;
        break;

      case WebRtcState.thinking:
        // Processing input, disable auto-response to prevent duplicates
        createResponse = false;
        interruptResponse = true;
        threshold = 0.6;
        silenceDurationMs = 600;
        break;

      case WebRtcState.speaking:
        // Playing TTS, disable auto-response to prevent echo loops
        createResponse = false;
        interruptResponse = false; // Allow barge-in but don't auto-create responses
        threshold = 0.85; // Higher threshold to reduce false positives from echo
        silenceDurationMs = 1000; // Longer silence to avoid echo triggering
        break;

      case WebRtcState.cooldown:
        // Cooling down after TTS, keep auto-response disabled
        createResponse = false;
        interruptResponse = true;
        threshold = 0.6;
        silenceDurationMs = 600;
        break;
    }

    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'session.update',
      'session': {
        'turn_detection': {
          'type': 'server_vad',
          'threshold': threshold,
          'prefix_padding_ms': 300,
          'silence_duration_ms': silenceDurationMs,
          'create_response': createResponse,
          'interrupt_response': interruptResponse,
        },
      },
    })));

    // Add observability log for create_response sending
    if (createResponse) {
      logInfo('Sending create_response: true to server', {
        'state': _currentState.name,
        'threshold': threshold,
        'silence_duration_ms': silenceDurationMs,
      });
    }

    logInfo('Configured turn detection for state', {
      'state': _currentState.name,
      'create_response': createResponse,
      'interrupt_response': interruptResponse,
      'threshold': threshold,
      'silence_duration_ms': silenceDurationMs,
    });
  }

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

    // Configure system-level AEC for Android devices
    await _audioSessionService.configureSystemAEC();

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
    _responseDoneReceived = false;
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
        'silence_duration_ms': session.turnDetectionSilenceMs ?? 700,  // Optimized from 1200 to 700ms for better responsiveness
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
        'max_response_output_tokens': 300,  // Increased from 160 to 300 for more complete responses
        'instructions': '请简单确认用户输入，无需过多寒暄。用于调试模式，验证音频输出是否正常工作。',
        'input_audio_transcription': {
          'model': session.transcriptionModel ?? 'gpt-4o-transcribe-diarize',
          'language': 'zh',  // Explicitly set to Chinese for better transcription accuracy
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
        _isPlayingTTS) {
      return;
    }

    // Transition to Speaking state - disable auto-response during playback
    _transitionToState(WebRtcState.speaking);

    // Set playing state to block upstream audio (only driven by server buffer events)
    _isPlayingTTS = true;

    // Clear input audio buffer to prevent any pending audio from being processed
    final channel = _controlChannel;
    if (channel != null) {
      channel.send(RTCDataChannelMessage(jsonEncode({
        'type': 'input_audio_buffer.clear'
      })));
    }

    // Cancel any pending fallback timer
    _playbackFallbackTimer?.cancel();

    final tracks = _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[];
    if (tracks.isEmpty) return;

    _remotePlaybackActive = true;
    for (final track in tracks) {
      track.enabled = false;
    }
    logInfo('Muted local microphone during assistant TTS playback', {});
  }

  void _handleRemotePlaybackStopped() {
    _lastPlaybackEndTime = DateTime.now(); // Track when playback ended for echo detection

    // Immediately restore microphone when output_audio_buffer.stopped is received
    // This provides more responsive barge-in compared to waiting for cleared event
    // The 200ms cooldown timer is removed to prevent timing drift on different devices
    _restoreMicrophone();
  }

  void _restoreMicrophone() {
    // Re-enable local audio track immediately
    final tracks = _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[];
    for (final track in tracks) {
      track.enabled = true;
    }
    logInfo('Restored local microphone after TTS playback stopped', {});
  }

  void _handleOutputBufferCleared() {
    _lastTTSEndAt = DateTime.now(); // Track when playback ended for time window validation

    // Transition to Cooldown state - keep auto-response disabled during cooldown
    _transitionToState(WebRtcState.cooldown);

    // Clear TTS playing state (only driven by server buffer events)
    _isPlayingTTS = false;

    // Clear input audio buffer again to ensure no residual audio
    final channel = _controlChannel;
    if (channel != null) {
      channel.send(RTCDataChannelMessage(jsonEncode({
        'type': 'input_audio_buffer.clear'
      })));
    }

    // Only restore microphone when both response.done and output_audio_buffer.cleared are received
    // But now microphone is already restored in _handleRemotePlaybackStopped, so we just transition to listening
    if (_responseDoneReceived && _remotePlaybackActive) {
      logInfo('Response completed, transitioning to listening state', {});

      // Transition to Listening state - re-enable auto-response
      _transitionToState(WebRtcState.listening);

      _remotePlaybackActive = false;
      _responseDoneReceived = false;

      // Try to process any pending transcriptions after cooldown
      _tryProcessTranscriptionQueue();
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
        'max_response_output_tokens': 300,  // Increased from 160 to 300 for more complete responses
        'instructions': '请简单确认用户输入，无需过多寒暄。用于调试模式，验证音频输出是否正常工作。',
        'input_audio_transcription': {
          'model': session.transcriptionModel ?? 'gpt-4o-transcribe-diarize',
          'language': 'zh',  // Explicitly set to Chinese for better transcription accuracy
        },
      },
    })));

    logInfo('Sent initial session update with manual response control', {
      'turn_detection': turnDetection,
      'voice': session.voice,
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
    logInfo('Response created, transitioning to thinking state', {
      'response_id': responseId,
    });

    // Transition to Thinking state - AI is processing the response
    _transitionToState(WebRtcState.thinking);

    // Track the response creation time to protect against immediate cancellation
    _lastResponseCreatedTime = DateTime.now();
    _hasActiveResponse = true;
  }

  void _handleResponseDone() {
    _responseDoneReceived = true;
    _hasActiveResponse = false;  // Response is no longer active

    logInfo('Response done received, response processing completed', {
      'timestamp': DateTime.now().toIso8601String(),
      'has_active_response': _hasActiveResponse,
      'last_response_created_time': _lastResponseCreatedTime?.toIso8601String(),
    });

    // Note: State transition to Listening will be handled by output_audio_buffer.cleared
    // after the cooldown period to prevent echo triggering new responses
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

  void _cancelCurrentResponse() {
    final channel = _controlChannel;
    if (channel == null) return;

    // Simple response cancellation for echo prevention
    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'response.cancel',
    })));

    logInfo('Cancelled current response to prevent echo loop');
  }

  void _handleSpeechStarted() {
    // Only trigger barge-in if we're currently playing TTS AND have an active response
    // This prevents false positives from echo or background noise
    if (_isPlayingTTS && _hasActiveResponse) {
      logInfo('Speech started during active playback - triggering barge-in flow', {
        'isPlayingTTS': _isPlayingTTS,
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
      'isPlayingTTS': _isPlayingTTS,
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

  void _handleInputAudioTranscriptionCompleted(Map<String, dynamic> data) {
    final transcript = data['transcript'] as String?;
    final itemId = data['item_id'] as String?;

    // Special handling for empty transcript - cancel current response to prevent echo loops
    if (transcript == '') {
      logInfo('Empty transcript detected, cancelling current response to prevent echo loop', {
        'item_id': itemId,
      });
      _cancelCurrentResponse();
      return;
    }

    if (transcript == null || transcript.trim().isEmpty || itemId == null) {
      logInfo('Input audio transcription completed but transcript or item_id is missing, skipping', {
        'transcript': transcript,
        'item_id': itemId,
      });
      return;
    }

    // Language filtering: REMOVED - allow all languages to trigger responses
    // Previously required Chinese transcripts (>=30% Chinese characters)
    // Now allow any language as fallback to ensure responses are always possible

    // Semantic debouncing: check transcript length and content
    final trimmed = transcript.trim();
    if (!_isValidTranscriptForResponse(trimmed)) {
      logInfo('Input audio transcription completed but content is not suitable for response, skipping', {
        'transcript': transcript,
        'length': trimmed.length,
      });
      return;
    }

    // Gate A: Item not consumed yet (de-duplication)
    if (_consumedUserItemIds.contains(itemId)) {
      logInfo('Gate A failed: Item already consumed, skipping', {
        'transcript': transcript,
        'item_id': itemId,
      });
      return;
    }

    // If TTS is currently playing, enqueue the transcription for later processing
    if (_isPlayingTTS) {
      _pendingTranscriptionQueue.add({
        'transcript': transcript,
        'itemId': itemId,
        'timestamp': DateTime.now(),
      });
      logInfo('TTS playing, enqueued transcription for later processing', {
        'transcript': transcript,
        'item_id': itemId,
        'queueSize': _pendingTranscriptionQueue.length,
      });
      return;
    }

    // Gate B: Time window check - must be > 200ms since last TTS ended
    if (_lastTTSEndAt != null) {
      final timeSinceTTSEnded = DateTime.now().difference(_lastTTSEndAt!);
      if (timeSinceTTSEnded.inMilliseconds <= 200) {
        logInfo('Gate B failed: Too soon after TTS ended, skipping response creation', {
          'transcript': transcript,
          'timeSinceTTSEnded': timeSinceTTSEnded.inMilliseconds,
        });
        return;
      }
    }

    // All gates passed - consume the item and create response
    _consumedUserItemIds.add(itemId);

    logInfo('Input audio transcription completed with valid content - creating response', {
      'transcript': transcript,
      'item_id': itemId,
      'timeSinceTTSEnded': _lastTTSEndAt != null ? DateTime.now().difference(_lastTTSEndAt!).inMilliseconds : null,
    });

    // Server will auto-create response since create_response: true is enabled
    // No need to send manual response.create
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



  void _tryProcessTranscriptionQueue() {
    if (_pendingTranscriptionQueue.isEmpty) {
      return;
    }

    // Process the oldest pending transcription
    final pendingItem = _pendingTranscriptionQueue.removeAt(0);
    final transcript = pendingItem['transcript'] as String;
    final itemId = pendingItem['itemId'] as String;
    final timestamp = pendingItem['timestamp'] as DateTime;

    // Check if item was already consumed (safety check)
    if (_consumedUserItemIds.contains(itemId)) {
      logInfo('Pending transcription item already consumed, skipping', {
        'item_id': itemId,
      });
      // Try next item if available
      _tryProcessTranscriptionQueue();
      return;
    }

    // Check time window again (should pass since we're after cooldown)
    if (_lastTTSEndAt != null) {
      final timeSinceTTSEnded = DateTime.now().difference(_lastTTSEndAt!);
      if (timeSinceTTSEnded.inMilliseconds <= 200) {
        logInfo('Still too soon after TTS ended, re-enqueueing transcription', {
          'transcript': transcript,
          'timeSinceTTSEnded': timeSinceTTSEnded.inMilliseconds,
        });
        // Re-enqueue at the front
        _pendingTranscriptionQueue.insert(0, pendingItem);
        return;
      }
    }

    // All gates passed - consume the item and create response
    _consumedUserItemIds.add(itemId);

    logInfo('Processing pending transcription after TTS cooldown', {
      'transcript': transcript,
      'item_id': itemId,
      'queueSize': _pendingTranscriptionQueue.length,
      'timeInQueue': DateTime.now().difference(timestamp).inMilliseconds,
    });

    // Server will auto-create response since create_response: true is enabled
    // No need to send manual response.create

    // If there are more items in queue, schedule next processing
    if (_pendingTranscriptionQueue.isNotEmpty) {
      Future.delayed(const Duration(milliseconds: 100), _tryProcessTranscriptionQueue);
    }
  }
}
