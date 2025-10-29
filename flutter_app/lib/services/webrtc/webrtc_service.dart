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

  final _metricsController = StreamController<LatencyMetrics>.broadcast();

  Stream<LatencyMetrics> get metricsStream => _metricsController.stream;

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
      logInfo('Local ICE candidate gathered', {
        'candidate': candidate.candidate,
        'sdpMid': candidate.sdpMid,
        'sdpMLineIndex': candidate.sdpMLineIndex,
      });
    };
    pc.onDataChannel = _attachDataChannel;
    pc.onTrack = (event) {
      logInfo('Remote track received', {
        'trackId': event.track.id,
        'kind': event.track.kind,
      });
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
    await _controlChannel?.close();
    _controlChannel = null;

    _remotePlaybackActive = false;
    _localMicEnabledBeforePlayback = null;
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
        logInfo('WebRTC data message', {
          'binary': true,
          'length': message.binary.length,
        });
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
            if (session.turnDetectionThreshold != null)
              'threshold': session.turnDetectionThreshold,
            if (session.turnDetectionSilenceMs != null)
              'silence_duration_ms': session.turnDetectionSilenceMs,
          };

    send({
      'type': 'session.update',
      'session': {
        'modalities': ['audio', 'text'],
        'voice': session.voice,
        'turn_detection': turnDetection,
        'output_audio_format': {'type': 'pcm16'},
        if (session.transcriptionModel != null)
          'input_audio_transcription': {'model': session.transcriptionModel},
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
      case 'output_audio_buffer.completed':
      case 'output_audio_buffer.done':
      case 'output_audio_buffer.cleared':
      case 'output_audio_buffer.stopped':
      case 'response.done':
        _handleRemotePlaybackFinished();
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

    final tracks = _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[];
    if (tracks.isEmpty) return;

    _remotePlaybackActive = true;
    _localMicEnabledBeforePlayback = tracks.first.enabled;
    for (final track in tracks) {
      track.enabled = false;
    }
    logInfo('Muted local microphone during assistant playback', {});
  }

  void _handleRemotePlaybackFinished() {
    if (!_remotePlaybackActive) return;
    final targetState = _localMicEnabledBeforePlayback;
    _remotePlaybackActive = false;
    _localMicEnabledBeforePlayback = null;

    if (targetState == null) {
      return;
    }
    final tracks = _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[];
    for (final track in tracks) {
      track.enabled = targetState;
    }
    logInfo('Restored local microphone state after assistant playback', {});
  }
}
