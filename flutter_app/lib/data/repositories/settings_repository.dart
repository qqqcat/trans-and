import '../../services/settings/settings_storage.dart';

class SettingsRepository {
  SettingsRepository() : _storage = SettingsStorage();

  final SettingsStorage _storage;

  Future<String> getApiEndpoint() => _storage.getApiEndpoint();

  Future<void> setApiEndpoint(String? endpoint) =>
      _storage.setApiEndpoint(endpoint);

  Future<String> getRealtimeDeployment() => _storage.getRealtimeDeployment();

  Future<void> setRealtimeDeployment(String deployment) =>
      _storage.setRealtimeDeployment(deployment);

  Future<String> getResponsesDeployment() => _storage.getResponsesDeployment();

  Future<void> setResponsesDeployment(String deployment) =>
      _storage.setResponsesDeployment(deployment);

  Future<String> getWebRtcUrl() => _storage.getWebRtcUrl();

  Future<void> setWebRtcUrl(String url) => _storage.setWebRtcUrl(url);
}
