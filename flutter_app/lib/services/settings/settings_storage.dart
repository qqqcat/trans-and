import 'package:shared_preferences/shared_preferences.dart';

import '../../core/config/app_config.dart';

class SettingsStorage {
  static const _apiEndpointKey = 'api_endpoint';
  static const _realtimeDeploymentKey = 'realtime_deployment';
  static const _responsesDeploymentKey = 'responses_deployment';
  static const _webRtcUrlKey = 'webrtc_url';
  static const _realtimeTranscriptionModelKey = 'realtime_transcription_model';
  static const _realtimeTurnDetectionModeKey = 'realtime_turn_detection_mode';
  static const _realtimeTurnDetectionThresholdKey =
      'realtime_turn_detection_threshold';
  static const _realtimeTurnDetectionSilenceKey =
      'realtime_turn_detection_silence_ms';
  static const _muteMicDuringPlaybackKey = 'realtime_mute_mic_during_playback';

  Future<SharedPreferences> get _prefs async =>
      await SharedPreferences.getInstance();

  Future<String> getApiEndpoint() async {
    final prefs = await _prefs;
    return prefs.getString(_apiEndpointKey) ?? AppConfig.instance.endpoint;
  }

  Future<void> setApiEndpoint(String? endpoint) async {
    final prefs = await _prefs;
    final normalized = endpoint == null
        ? null
        : AppConfig.normalizeEndpoint(endpoint);
    if (normalized == null ||
        normalized.isEmpty ||
        normalized == AppConfig.instance.endpoint) {
      await prefs.remove(_apiEndpointKey);
    } else {
      await prefs.setString(_apiEndpointKey, normalized);
    }
  }

  Future<String> getRealtimeDeployment() async {
    final prefs = await _prefs;
    final stored = prefs.getString(_realtimeDeploymentKey);
    final fallback = AppConfig.instance.realtimeDeployment;
    return (stored ?? fallback ?? '').trim();
  }

  Future<void> setRealtimeDeployment(String deployment) async {
    final prefs = await _prefs;
    if (deployment.isEmpty ||
        deployment == (AppConfig.instance.realtimeDeployment ?? '')) {
      await prefs.remove(_realtimeDeploymentKey);
    } else {
      await prefs.setString(_realtimeDeploymentKey, deployment);
    }
  }

  Future<String> getResponsesDeployment() async {
    final prefs = await _prefs;
    final stored = prefs.getString(_responsesDeploymentKey);
    final fallback = AppConfig.instance.responsesDeployment;
    return (stored ?? fallback ?? '').trim();
  }

  Future<void> setResponsesDeployment(String deployment) async {
    final prefs = await _prefs;
    if (deployment.isEmpty ||
        deployment == (AppConfig.instance.responsesDeployment ?? '')) {
      await prefs.remove(_responsesDeploymentKey);
    } else {
      await prefs.setString(_responsesDeploymentKey, deployment);
    }
  }

  Future<String> getWebRtcUrl() async {
    final prefs = await _prefs;
    final stored = prefs.getString(_webRtcUrlKey);
    final fallback = AppConfig.instance.realtimeWebRtcUrl;
    return (stored ?? fallback ?? '').trim();
  }

  Future<void> setWebRtcUrl(String url) async {
    final prefs = await _prefs;
    if (url.isEmpty || url == (AppConfig.instance.realtimeWebRtcUrl ?? '')) {
      await prefs.remove(_webRtcUrlKey);
    } else {
      await prefs.setString(_webRtcUrlKey, url);
    }
  }

  Future<String?> getRealtimeTranscriptionModel() async {
    final prefs = await _prefs;
    final stored = _normalize(prefs.getString(_realtimeTranscriptionModelKey));
    final fallback = _normalize(AppConfig.instance.realtimeTranscriptionModel);
    return stored ?? fallback;
  }

  Future<String> getTurnDetectionMode() async {
    final prefs = await _prefs;
    final stored = _normalize(prefs.getString(_realtimeTurnDetectionModeKey));
    final fallback =
        _normalize(AppConfig.instance.realtimeTurnDetectionMode) ??
        'server_vad';
    switch (stored ?? fallback) {
      case 'none':
        return 'none';
      default:
        return 'server_vad';
    }
  }

  Future<double?> getTurnDetectionThreshold() async {
    final prefs = await _prefs;
    final stored = prefs.getDouble(_realtimeTurnDetectionThresholdKey);
    final fallback = AppConfig.instance.realtimeTurnDetectionThreshold;
    return stored ?? fallback;
  }

  Future<int?> getTurnDetectionSilenceMs() async {
    final prefs = await _prefs;
    final stored = prefs.getInt(_realtimeTurnDetectionSilenceKey);
    final fallback = AppConfig.instance.realtimeTurnDetectionSilenceMs;
    return stored ?? fallback;
  }

  Future<bool> getMuteMicDuringPlayback() async {
    final prefs = await _prefs;
    final stored = prefs.getBool(_muteMicDuringPlaybackKey);
    return stored ?? AppConfig.instance.muteMicDuringPlayback;
  }

  String? _normalize(String? value) {
    if (value == null) return null;
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : trimmed;
  }
}
