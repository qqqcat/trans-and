import 'package:audio_session/audio_session.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:just_audio/just_audio.dart';
import 'package:permission_handler/permission_handler.dart';

class AudioSessionService {
  AudioSessionService();

  AudioSession? _session;
  final AudioPlayer _player = AudioPlayer();
  RTCVideoRenderer? _remoteRenderer;
  bool _rendererInitialized = false;

  Future<void> startCapture() async {
    await _ensureMicrophonePermission();
    _session ??= await AudioSession.instance;
    await _session!.configure(AudioSessionConfiguration(
      avAudioSessionCategory: AVAudioSessionCategory.playAndRecord,
      avAudioSessionCategoryOptions: AVAudioSessionCategoryOptions.defaultToSpeaker | AVAudioSessionCategoryOptions.allowBluetooth,
      avAudioSessionMode: AVAudioSessionMode.voiceChat,
      androidAudioAttributes: const AndroidAudioAttributes(
        contentType: AndroidAudioContentType.speech,
        flags: AndroidAudioFlags.none,
        usage: AndroidAudioUsage.voiceCommunication,
      ),
      androidAudioFocusGainType: AndroidAudioFocusGainType.gain,
      androidWillPauseWhenDucked: false,
    ));
    // Actual microphone capture will be handled by flutter_webrtc.
  }

  Future<void> stopCapture() async {
    await _player.stop();
    await detachRemoteStream();
  }

  Future<void> attachRemoteStream(MediaStream stream) async {
    _remoteRenderer ??= RTCVideoRenderer();
    if (!_rendererInitialized) {
      await _remoteRenderer!.initialize();
      _rendererInitialized = true;
    }
    _remoteRenderer!.srcObject = stream;

    // Configure audio routing for voice communication
    // Note: setSpeakerphoneOn is deprecated in API 34+, but flutter_webrtc may not have updated yet
    // Using AudioSession configuration above should handle routing properly
    try {
      await Helper.setSpeakerphoneOn(true);
    } catch (e) {
      // Fallback if setSpeakerphoneOn fails on newer Android versions
      // The AudioSession configuration should handle routing
    }
  }

  Future<void> detachRemoteStream() async {
    if (_remoteRenderer != null) {
      _remoteRenderer!.srcObject = null;
      await _remoteRenderer!.dispose();
      _remoteRenderer = null;
      _rendererInitialized = false;
    }
  }

  Future<void> _ensureMicrophonePermission() async {
    var status = await Permission.microphone.status;
    if (status.isGranted) {
      return;
    }

    if (status.isPermanentlyDenied) {
      await openAppSettings();
      throw StateError(
        'Microphone permission has been permanently denied. Please enable it in system settings.',
      );
    }

    status = await Permission.microphone.request();
    if (status.isGranted) {
      return;
    }

    if (status.isPermanentlyDenied) {
      await openAppSettings();
      throw StateError(
        'Microphone permission has been permanently denied. Please enable it in system settings.',
      );
    }

    throw StateError('Microphone permission is required to start capture.');
  }
}
