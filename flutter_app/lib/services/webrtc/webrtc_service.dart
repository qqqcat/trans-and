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

  // Fallback timer for microphone restoration
  Timer? _playbackFallbackTimer;

  // Track response creation time to protect against immediate cancellation
  DateTime? _lastResponseCreatedTime;

  // Track if there's currently an active response in progress
  bool _hasActiveResponse = false;

  // Track consumed user item IDs to prevent duplicate responses
  final Set<String> _consumedUserItemIds = {};

  // State machine for echo prevention
  WebRtcState _currentState = WebRtcState.idle;

  final _metricsController = StreamController<LatencyMetrics>.broadcast();

  Stream<LatencyMetrics> get metricsStream => _metricsController.stream;

  /// Get current state for debugging
  WebRtcState get currentState => _currentState;

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
        'input_audio_transcription': {
          'model': session.transcriptionModel ?? 'gpt-realtime-mini',
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
    } catch (error) {
      logWarning('Failed to decode realtime control message', {
        'payload': payload,
        'error': error.toString(),
      });
      return;
    }

    if (data == null) return;
    final type = data['type'] as String?;
    if (type == null) return;

    // Simplified event handling based on working example project
    // Only handle core events needed for basic conversation flow
    switch (type) {
      case 'response.audio_transcript.done':
        // Handle AI transcript completion (like example project)
        final transcript = data['transcript'] as String?;
        if (transcript != null && transcript.isNotEmpty) {
          logInfo('AI transcript completed', {'transcript': transcript});
          // Simplified: just log for now, UI can listen to other events if needed
        }
        break;

      case 'conversation.item.input_audio_transcription.completed':
        // Handle user input transcription (simplified version)
        _handleInputAudioTranscriptionCompleted(data);
        break;

      default:
        // Ignore other events to reduce complexity
        break;
    }
  }

  // Removed all complex event handlers to simplify like the working example project
  // Only keeping core transcription handling to prevent repeat loops and ensure responses

  Future<void> _sendInitialSessionUpdate(RealtimeSession session) async {
    final channel = _controlChannel;
    if (channel == null) return;

    final turnDetection = {
      'type': 'server_vad',
      'threshold': session.turnDetectionThreshold ?? 0.6,
      'prefix_padding_ms': 300,
      'silence_duration_ms': 600,
      'create_response': true,  // Enable auto-response for simplified approach
      'interrupt_response': true,
    };

    channel.send(RTCDataChannelMessage(jsonEncode({
      'type': 'session.update',
      'session': {
        'modalities': ['audio', 'text'],
        'voice': session.voice,
        'turn_detection': turnDetection,
        'output_audio_format': 'pcm16',
        'max_response_output_tokens': 300,
        'input_audio_transcription': {
          'model': session.transcriptionModel ?? 'gpt-realtime-mini',
          'language': 'zh',
        },
      },
    })));

    logInfo('Sent initial session update with auto-response enabled', {
      'turn_detection': turnDetection,
      'voice': session.voice,
    });
  }

  void _handleInputAudioTranscriptionCompleted(Map<String, dynamic> data) {
    final transcript = data['transcript'] as String?;
    final itemId = data['item_id'] as String?;

    // Simplified approach based on working example:
    // - Remove complex filtering and queues like the example project
    // - Process transcript directly, only skip if completely empty

    if (transcript == null || transcript.trim().isEmpty || itemId == null) {
      logInfo('Transcript or item_id missing, skipping', {
        'transcript': transcript,
        'item_id': itemId,
      });
      return;
    }

    // Basic duplicate check (keep minimal to avoid blocking responses)
    if (_consumedUserItemIds.contains(itemId)) {
      logInfo('Transcript already processed, skipping', {
        'transcript': transcript,
        'item_id': itemId,
      });
      return;
    }

    // Mark as consumed and process directly
    _consumedUserItemIds.add(itemId);

    logInfo('Processing user transcript directly (simplified approach)', {
      'transcript': transcript,
      'item_id': itemId,
    });

    // Server will auto-create response since create_response: true is enabled
    // No complex queue management, time windows, or content validation needed
  }
}
