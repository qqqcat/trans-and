import 'app_config_loader.dart';
import 'platform_environment.dart';

class AppConfig {
  AppConfig._({
    required this.endpoint,
    required this.apiKey,
    this.realtimeDeployment,
    this.responsesDeployment,
    this.realtimeWebRtcUrl,
  });

  static AppConfig? _instance;

  final String endpoint;
  final String apiKey;
  final String? realtimeDeployment;
  final String? responsesDeployment;
  final String? realtimeWebRtcUrl;

  static AppConfig get instance {
    final config = _instance;
    if (config == null) {
      throw StateError(
        'AppConfig has not been loaded. Call AppConfig.load() before accessing instance.',
      );
    }
    return config;
  }

  static Future<void> load() async {
    if (_instance != null) return;

    String? endpoint = _firstNonEmpty([
      const String.fromEnvironment('AZURE_OPENAI_ENDPOINT'),
      readPlatformEnvironment('AZURE_OPENAI_ENDPOINT'),
    ]);

    String? apiKey = _firstNonEmpty([
      const String.fromEnvironment('AZURE_OPENAI_API_KEY'),
      readPlatformEnvironment('AZURE_OPENAI_API_KEY'),
    ]);

    String? realtimeDeployment = _firstNonEmpty([
      const String.fromEnvironment('AZURE_REALTIME_DEPLOYMENT'),
      readPlatformEnvironment('AZURE_REALTIME_DEPLOYMENT'),
    ]);

    String? responsesDeployment = _firstNonEmpty([
      const String.fromEnvironment('AZURE_RESPONSES_DEPLOYMENT'),
      readPlatformEnvironment('AZURE_RESPONSES_DEPLOYMENT'),
    ]);

    String? realtimeWebRtcUrl = _firstNonEmpty([
      const String.fromEnvironment('AZURE_REALTIME_WEBRTC_URL'),
      readPlatformEnvironment('AZURE_REALTIME_WEBRTC_URL'),
    ]);

    if (endpoint == null || apiKey == null) {
      final properties = await loadLocalProperties();
      if (properties != null) {
        endpoint ??= _normalizeEmpty(properties['azure.openai.endpoint'] ?? '');
        apiKey ??= _normalizeEmpty(properties['azure.openai.apiKey'] ?? '');
        realtimeDeployment ??= _normalizeEmpty(
          properties['azure.openai.realtimeDeployment'] ?? '',
        );
        responsesDeployment ??= _normalizeEmpty(
          properties['azure.openai.responsesDeployment'] ?? '',
        );
        realtimeWebRtcUrl ??= _normalizeEmpty(
          properties['azure.openai.realtimeWebRtcUrl'] ?? '',
        );
      }
    }

    if (endpoint == null || apiKey == null) {
      throw StateError(
        'Azure OpenAI endpoint or API key is not configured. '
        'Set AZURE_OPENAI_ENDPOINT / AZURE_OPENAI_API_KEY environment variables '
        'or provide values in local.properties.',
      );
    }

    _instance = AppConfig._(
      endpoint: _normalizeEndpoint(endpoint),
      apiKey: apiKey,
      realtimeDeployment: realtimeDeployment,
      responsesDeployment: responsesDeployment,
      realtimeWebRtcUrl: realtimeWebRtcUrl == null || realtimeWebRtcUrl.isEmpty
          ? null
          : _normalizeBaseUrl(realtimeWebRtcUrl),
    );
  }

  static String _normalizeEndpoint(String value) {
    final stripped = value.trim().replaceAll(RegExp(r'/+$'), '');
    return '$stripped/';
  }

  static String _normalizeBaseUrl(String value) {
    return value.trim().replaceAll(RegExp(r'/+$'), '');
  }

  static String normalizeEndpoint(String value) => _normalizeEndpoint(value);
}

String? _firstNonEmpty(List<String?> candidates) {
  for (final candidate in candidates) {
    final value = _normalizeEmpty(candidate ?? '');
    if (value != null) {
      return value;
    }
  }
  return null;
}

String? _normalizeEmpty(String? value) {
  final trimmed = value?.trim();
  if (trimmed == null || trimmed.isEmpty) {
    return null;
  }
  return trimmed;
}
