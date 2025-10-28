import 'dart:async';

import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../../core/logging/logger.dart';
import '../../domain/models/session_models.dart';
import '../../services/realtime/realtime_api_client.dart';

class WebRtcService {
  WebRtcService();
  RTCPeerConnection? _peerConnection;
  MediaStream? _localStream;
  MediaStream? _remoteStream;
  WebSocketChannel? _dataChannel;

  final _metricsController = StreamController<LatencyMetrics>.broadcast();

  Stream<LatencyMetrics> get metricsStream => _metricsController.stream;

  Future<void> connect(RealtimeSession session) async {
    final iceServers = session.iceServers.isNotEmpty
        ? session.iceServers
            .map(
              (server) => {
                'urls': server.urls,
                if (server.username != null) 'username': server.username,
                if (server.credential != null) 'credential': server.credential,
              },
            )
            .toList()
        : [
            {'urls': 'stun:stun.l.google.com:19302'}
          ];

    final configuration = {
      'iceServers': iceServers,
    };
    _peerConnection = await createPeerConnection(configuration);
    _peerConnection?.onConnectionState = (state) {
      logInfo('WebRTC connection state: $state');
    };

    _remoteStream = await createLocalMediaStream('remote');
    _peerConnection?.onTrack = (event) {
      if (event.streams.isNotEmpty) {
        _remoteStream = event.streams.first;
      }
    };

    // Since this is a placeholder we skip actual offer/answer negotiation.
    _metricsController.add(const LatencyMetrics(
      asrMs: 100,
      translationMs: 250,
      ttsMs: 190,
    ));
  }

  Future<void> disconnect() async {
    await _dataChannel?.sink.close();
    await _peerConnection?.close();
    await _localStream?.dispose();
    await _remoteStream?.dispose();
    _dataChannel = null;
    _peerConnection = null;
    _localStream = null;
    _remoteStream = null;
  }
}
