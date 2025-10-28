import 'dart:async';

import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../core/logging/logger.dart';
import '../../domain/models/session_models.dart';
import '../../services/realtime/realtime_api_client.dart';

class WebRtcService {
  WebRtcService({required RealtimeApiClient realtimeApiClient})
    : _realtimeApiClient = realtimeApiClient;

  final RealtimeApiClient _realtimeApiClient;

  RTCPeerConnection? _peerConnection;
  RTCDataChannel? _controlChannel;

  final _metricsController = StreamController<LatencyMetrics>.broadcast();

  Stream<LatencyMetrics> get metricsStream => _metricsController.stream;

  Future<void> connect(RealtimeSession session) async {
    await disconnect();

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
    pc.onDataChannel = (dataChannel) {
      _attachDataChannel(dataChannel);
    };
    pc.onTrack = (event) {
      logInfo('Remote track received', {
        'trackId': event.track.id,
        'kind': event.track.kind,
      });
    };

    // Create a control/data channel for low-latency events.
    final dataChannelInit = RTCDataChannelInit()..ordered = true;
    final dataChannel = await pc.createDataChannel(
      'oai-events',
      dataChannelInit,
    );
    _attachDataChannel(dataChannel);

    final offer = await pc.createOffer({
      'offerToReceiveAudio': true,
      'offerToReceiveVideo': false,
    });
    await pc.setLocalDescription(offer);
    final localSdp = await _waitForCompleteLocalSdp(pc);

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

    await _peerConnection?.close();
    _peerConnection = null;
  }

  void _attachDataChannel(RTCDataChannel channel) {
    _controlChannel = channel;
    channel.onMessage = (message) {
      logInfo('WebRTC data message', {
        'binary': message.isBinary,
        'text': message.isBinary ? null : message.text,
      });
    };
    channel.onDataChannelState = (state) {
      logInfo('WebRTC data channel state: $state');
    };
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
}
