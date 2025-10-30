import 'app_config_loader.dart';
import 'platform_environment.dart';

class AppConfig {
  AppConfig._({
    required this.endpoint,
    required this.apiKey,
    this.realtimeDeployment,
    this.responsesDeployment,
    this.realtimeWebRtcUrl,
    this.realtimeTranscriptionModel,
    this.realtimeTurnDetectionMode,
    this.realtimeTurnDetectionThreshold,
    this.realtimeTurnDetectionSilenceMs,
    required this.muteMicDuringPlayback,
  });

  static AppConfig? _instance;

  final String endpoint;
  final String apiKey;
  final String? realtimeDeployment;
  final String? responsesDeployment;
  final String? realtimeWebRtcUrl;
  final String? realtimeTranscriptionModel;
  final String? realtimeTurnDetectionMode;
  final double? realtimeTurnDetectionThreshold;
  final int? realtimeTurnDetectionSilenceMs;
  final bool muteMicDuringPlayback;

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

    String? realtimeTranscriptionModel = _firstNonEmpty([
      const String.fromEnvironment('AZURE_REALTIME_TRANSCRIPTION_MODEL'),
      readPlatformEnvironment('AZURE_REALTIME_TRANSCRIPTION_MODEL'),
    ]);

    String? realtimeTurnDetectionMode = _firstNonEmpty([
      const String.fromEnvironment('AZURE_REALTIME_TURN_DETECTION'),
      readPlatformEnvironment('AZURE_REALTIME_TURN_DETECTION'),
    ]);

    String? realtimeTurnDetectionThresholdRaw = _firstNonEmpty([
      const String.fromEnvironment('AZURE_REALTIME_TURN_DETECTION_THRESHOLD'),
      readPlatformEnvironment('AZURE_REALTIME_TURN_DETECTION_THRESHOLD'),
    ]);

    String? realtimeTurnDetectionSilenceRaw = _firstNonEmpty([
      const String.fromEnvironment('AZURE_REALTIME_TURN_DETECTION_SILENCE_MS'),
      readPlatformEnvironment('AZURE_REALTIME_TURN_DETECTION_SILENCE_MS'),
    ]);

    String? muteMicDuringPlaybackRaw = _firstNonEmpty([
      const String.fromEnvironment('AZURE_REALTIME_MUTE_MIC_DURING_PLAYBACK'),
      readPlatformEnvironment('AZURE_REALTIME_MUTE_MIC_DURING_PLAYBACK'),
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
        realtimeTranscriptionModel ??= _normalizeEmpty(
          properties['azure.openai.realtimeTranscriptionModel'] ?? '',
        );
        realtimeTurnDetectionMode ??= _normalizeEmpty(
          properties['azure.openai.realtimeTurnDetection'] ?? '',
        );
        realtimeTurnDetectionThresholdRaw ??= _normalizeEmpty(
          properties['azure.openai.realtimeTurnDetectionThreshold'] ?? '',
        );
        realtimeTurnDetectionSilenceRaw ??= _normalizeEmpty(
          properties['azure.openai.realtimeTurnDetectionSilenceMs'] ?? '',
        );
        muteMicDuringPlaybackRaw ??= _normalizeEmpty(
          properties['azure.openai.realtimeMuteMicDuringPlayback'] ?? '',
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

    final double? realtimeTurnDetectionThreshold = _parseDouble(
      realtimeTurnDetectionThresholdRaw,
    ) ?? 0.5; // 默认0.5的检测阈值
    final int? realtimeTurnDetectionSilenceMs = _parseInt(
      realtimeTurnDetectionSilenceRaw,
    ) ?? 1000; // 默认1秒静音时间，减少过早回复
    final bool muteMicDuringPlayback =
        _parseBool(muteMicDuringPlaybackRaw) ?? true;

    _instance = AppConfig._(
      endpoint: _normalizeEndpoint(endpoint),
      apiKey: apiKey,
      realtimeDeployment: realtimeDeployment,
      responsesDeployment: responsesDeployment,
      realtimeWebRtcUrl: realtimeWebRtcUrl == null || realtimeWebRtcUrl.isEmpty
          ? null
          : _normalizeBaseUrl(realtimeWebRtcUrl),
      realtimeTranscriptionModel: realtimeTranscriptionModel,
      realtimeTurnDetectionMode: realtimeTurnDetectionMode,
      realtimeTurnDetectionThreshold: realtimeTurnDetectionThreshold,
      realtimeTurnDetectionSilenceMs: realtimeTurnDetectionSilenceMs,
      muteMicDuringPlayback: muteMicDuringPlayback,
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

double? _parseDouble(String? value) {
  if (value == null) return null;
  return double.tryParse(value.trim());
}

int? _parseInt(String? value) {
  if (value == null) return null;
  return int.tryParse(value.trim());
}

bool? _parseBool(String? value) {
  if (value == null) return null;
  switch (value.trim().toLowerCase()) {
    case '1':
    case 'true':
    case 'yes':
    case 'y':
    case 'on':
      return true;
    case '0':
    case 'false':
    case 'no':
    case 'off':
    case 'n':
      return false;
    default:
      return null;
  }
}
