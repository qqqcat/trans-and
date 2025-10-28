import 'package:shared_preferences/shared_preferences.dart';

import '../../core/config/app_config.dart';

class SettingsStorage {
  static const _apiEndpointKey = 'api_endpoint';
  static const _realtimeDeploymentKey = 'realtime_deployment';
  static const _responsesDeploymentKey = 'responses_deployment';
  static const _webRtcUrlKey = 'webrtc_url';

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
}
