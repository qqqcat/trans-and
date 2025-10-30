import 'package:audio_session/audio_session.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:just_audio/just_audio.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/services.dart';

import '../../core/logging/logger.dart';

class AudioSessionService {
  AudioSessionService();

  AudioSession? _session;
  final AudioPlayer _player = AudioPlayer();
  RTCVideoRenderer? _remoteRenderer;
  bool _rendererInitialized = false;

  static const platform = MethodChannel('com.example.transand_flutter/audio_session');

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

    // Configure audio routing for voice communication using modern Android APIs
    // Avoid deprecated setSpeakerphoneOn, use AudioManager#setCommunicationDevice instead
    try {
      // For Android API 31+, use setCommunicationDevice for better audio routing control
      // This prevents audio loopback issues and ensures proper AEC (Acoustic Echo Cancellation)
      await _configureAudioCommunicationDevice();
    } catch (e) {
      // Fallback to AudioSession configuration if setCommunicationDevice fails
      logWarning('Failed to configure communication device, using fallback', {'error': e.toString()});
      try {
        await Helper.setSpeakerphoneOn(true);
      } catch (fallbackError) {
        // If both fail, rely on AudioSession configuration only
        logWarning('Audio routing fallback also failed', {'error': fallbackError.toString()});
      }
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

  Future<void> _configureAudioCommunicationDevice() async {
    // For Android API 31+, configure communication device to prevent audio loopback
    // This is now implemented using native Android code for better control
    try {
      final success = await platform.invokeMethod('configureCommunicationDevice');
      if (success == true) {
        logInfo('Communication device configured successfully via native Android API', {});
      } else {
        logWarning('Communication device configuration returned false', {});
        // Fallback to AudioSession configuration
        await _fallbackAudioConfiguration();
      }
    } catch (e) {
      // Fallback to AudioSession configuration if native call fails
      logWarning('Failed to configure communication device via native API, using fallback', {'error': e.toString()});
      await _fallbackAudioConfiguration();
    }
  }

  Future<void> _fallbackAudioConfiguration() async {
    try {
      await Helper.setSpeakerphoneOn(true);
    } catch (fallbackError) {
      // If both fail, rely on AudioSession configuration only
      logWarning('Audio routing fallback also failed', {'error': fallbackError.toString()});
    }
  }

  Future<void> configureSystemAEC() async {
    // Attempt to enable system-level Acoustic Echo Cancellation
    // This now uses native Android implementation for better AEC control
    try {
      // Note: We need AudioRecord session ID, which may not be directly available from flutter_webrtc
      // For now, we'll attempt to configure all audio effects when we have a session
      // This is a best-effort implementation for Android devices that support it

      // Try to get session ID from WebRTC statistics (if available)
      // For now, we'll call the native method without session ID and let it handle the configuration
      final success = await platform.invokeMethod('configureAllAudioEffects', {'sessionId': 0});
      if (success == true) {
        logInfo('System AEC and audio effects configured successfully via native Android API', {});
      } else {
        logWarning('System AEC configuration returned false', {});
      }
    } catch (e) {
      logWarning('Failed to configure system AEC via native API', {'error': e.toString()});
    }
  }
}
