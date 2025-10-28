import 'package:audio_session/audio_session.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:just_audio/just_audio.dart';

class AudioSessionService {
  AudioSessionService();

  AudioSession? _session;
  final AudioPlayer _player = AudioPlayer();
  RTCVideoRenderer? _remoteRenderer;

  Future<void> startCapture() async {
    _session ??= await AudioSession.instance;
    await _session!.configure(const AudioSessionConfiguration.speech());
    // Actual microphone capture will be handled by flutter_webrtc.
  }

  Future<void> stopCapture() async {
    await _player.stop();
    await detachRemoteStream();
  }

  Future<void> attachRemoteStream(MediaStream stream) async {
    _remoteRenderer ??= RTCVideoRenderer();
    if (!_remoteRenderer!.initialized) {
      await _remoteRenderer!.initialize();
    }
    _remoteRenderer!
      ..objectFit = RTCVideoViewObjectFit.RTCVideoViewObjectFitCover
      ..srcObject = stream;
    await Helper.setSpeakerphoneOn(true);
  }

  Future<void> detachRemoteStream() async {
    if (_remoteRenderer != null) {
      _remoteRenderer!.srcObject = null;
      await _remoteRenderer!.dispose();
      _remoteRenderer = null;
    }
  }
}
