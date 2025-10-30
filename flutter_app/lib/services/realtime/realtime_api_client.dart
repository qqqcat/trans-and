import 'package:dio/dio.dart';

import '../../core/config/app_config.dart';
import '../../core/logging/logger.dart';
import '../../domain/models/session_models.dart';
import '../settings/settings_storage.dart';

class RealtimeSession {
  RealtimeSession({
    required this.sessionId,
    required this.ephemeralKey,
    required this.deployment,
    required this.resourceEndpoint,
    required this.webrtcEndpoint,
    required this.iceServers,
    required this.voice,
    required this.turnDetectionMode,
    this.turnDetectionThreshold,
    this.turnDetectionSilenceMs,
    this.transcriptionModel,
    required this.muteMicDuringPlayback,
  });

  final String sessionId;
  final String ephemeralKey;
  final String deployment;
  final Uri resourceEndpoint;
  final Uri webrtcEndpoint;
  final List<IceServer> iceServers;
  final String voice;
  final String turnDetectionMode;
  final double? turnDetectionThreshold;
  final int? turnDetectionSilenceMs;
  final String? transcriptionModel;
  final bool muteMicDuringPlayback;

  bool get isServerVadEnabled => turnDetectionMode != 'none';
}

class IceServer {
  const IceServer({required this.urls, this.username, this.credential});

  final List<String> urls;
  final String? username;
  final String? credential;
}

class RealtimeApiClient {
  RealtimeApiClient({SettingsStorage? settingsStorage, Dio? dio})
    : _settingsStorage = settingsStorage ?? SettingsStorage(),
      _dio = dio ?? Dio() {
    _dio.options = _dio.options.copyWith(
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 20),
      sendTimeout: const Duration(seconds: 20),
    );
  }

  static const _responsesApiVersion = '';
  static const _defaultRealtimeVoice = 'verse';
  static const double _defaultVadThreshold = 0.3;
  static const double _minVadThreshold = 0.2;
  static const double _maxVadThreshold = 0.8;
  static const int _defaultVadSilenceMs = 1200;  // Increased from 300 to 1200 for longer silence tolerance
  static const int _minVadSilenceMs = 200;
  static const int _maxVadSilenceMs = 2000;

  final SettingsStorage _settingsStorage;
  final Dio _dio;

  final Map<String, RealtimeSession> _sessions = {};
  RealtimeSession? _activeSession;


  Future<RealtimeSession> createSession() async {
    // Return existing active session if available to prevent multiple sessions
    if (_activeSession != null) {
      logInfo('Returning existing active session', {'sessionId': _activeSession!.sessionId});
      return _activeSession!;
    }

    final config = AppConfig.instance;
    final endpoint = await _settingsStorage.getApiEndpoint();
    final realtimeDeployment = await _settingsStorage.getRealtimeDeployment();
    final webRtcBaseUrl = await _settingsStorage.getWebRtcUrl();
    final preferences = await _loadPreferences();

    if (realtimeDeployment.isEmpty) {
      throw StateError('Realtime deployment is not configured.');
    }
    if (webRtcBaseUrl.isEmpty) {
      throw StateError('Realtime WebRTC URL is not configured.');
    }

    // Azure 官方要求：Sessions 用 api-key header，body 为 JSON { model, voice }
    // 参考：https://learn.microsoft.com/en-us/azure/ai-services/openai/realtime-audio
    final sessionsUrl = Uri.parse(
      '$endpoint/openai/realtimeapi/sessions?api-version=2025-04-01-preview',
    );
    final payload = {
      'model': realtimeDeployment, // 注意：这里是“部署名”，非裸模型名
      'voice': _defaultRealtimeVoice,
    };

    logInfo('Creating realtime session', {
      'uri': sessionsUrl.toString(),
      'deployment': realtimeDeployment,
      'turnDetection': preferences.turnDetectionMode,
      'turnDetectionThreshold': preferences.turnDetectionThreshold,
      'turnDetectionSilenceMs': preferences.turnDetectionSilenceMs,
      'muteMicDuringPlayback': preferences.muteMicDuringPlayback,
      'transcriptionModel': preferences.transcriptionModel,
    });

    final response = await _dio.postUri(
      sessionsUrl,
      data: payload,
      options: Options(
        headers: {
          'api-key': config.apiKey,
          'Content-Type': 'application/json',
        },
        responseType: ResponseType.json,
      ),
    );

    final data = response.data as Map<String, dynamic>;
    final sessionId = data['id'] as String?;
    final clientSecret =
        data['client_secret']?['value'] as String? ?? data['token'] as String?;
    if (sessionId == null || clientSecret == null) {
      throw StateError('Invalid session response from Azure OpenAI.');
    }

    final iceServers = (data['ice_servers'] as List<dynamic>? ?? [])
        .map(
          (server) => IceServer(
            urls: List<String>.from(server['urls'] ?? const []),
            username: server['username'] as String?,
            credential: server['credential'] as String?,
          ),
        )
        .toList();

    final realtimeSession = RealtimeSession(
      sessionId: sessionId,
      ephemeralKey: clientSecret,
      deployment: realtimeDeployment,
      resourceEndpoint: Uri.parse(endpoint),
      webrtcEndpoint: _buildWebRtcUri(webRtcBaseUrl, realtimeDeployment),
      iceServers: iceServers,
      voice: _defaultRealtimeVoice,
      turnDetectionMode: preferences.turnDetectionMode,
      turnDetectionThreshold: preferences.turnDetectionThreshold,
      turnDetectionSilenceMs: preferences.turnDetectionSilenceMs,
      transcriptionModel: preferences.transcriptionModel,
      muteMicDuringPlayback: preferences.muteMicDuringPlayback,
    );

    _sessions[sessionId] = realtimeSession;
    _activeSession = realtimeSession;
    logInfo('Realtime session issued', {
      'sessionId': sessionId,
      'webrtcEndpoint': realtimeSession.webrtcEndpoint.toString(),
      'ephemeralKeyPrefix': clientSecret.length <= 8
          ? clientSecret
          : '${clientSecret.substring(0, 4)}...$clientSecret.substring(clientSecret.length - 4)',
    });
    return realtimeSession;
  }

  Future<String> sendOfferAndGetAnswer({
    required String sessionId,
    required String offerSdp,
  }) async {
    final session = _sessions[sessionId];
    if (session == null) {
      throw StateError('No session found for id $sessionId');
    }

    logInfo('Sending WebRTC offer', {
      'endpoint': session.webrtcEndpoint.toString(),
      'deployment': session.deployment,
    });

    try {
      // Azure 官方要求：WebRTC SDP 交换用 Bearer + application/sdp + 纯文本 offer.sdp
      // 参考：https://learn.microsoft.com/en-us/azure/ai-services/openai/realtime-audio
      // 超时设置：网络环境差异大，TLS握手+负载均衡可能需30s以上
      final region = _extractRegionFromWebRtcUrl(session.webrtcEndpoint.toString());
      final deployment = session.deployment;
      final webrtcUrl = Uri.parse(
        'https://$region.realtimeapi-preview.ai.azure.com/v1/realtimertc?model=$deployment',
      );
      final dio = Dio(BaseOptions(
        connectTimeout: const Duration(seconds: 30),
        sendTimeout: const Duration(seconds: 30),
        receiveTimeout: const Duration(seconds: 60),
        responseType: ResponseType.plain,
        headers: {
          'Authorization': 'Bearer ${session.ephemeralKey}', // ephemeral key，一分钟有效
          'Content-Type': 'application/sdp', // 必须是 application/sdp，非 JSON
          'Accept': 'application/sdp',
        },
      ));
      final response = await dio.postUri(
        webrtcUrl,
        data: offerSdp, // 纯 SDP 文本，别包成 JSON
      );

      // 检查响应状态码（Azure OpenAI 返回 201 Created 表示成功）
      if (response.statusCode != 200 && response.statusCode != 201) {
        throw DioException(
          requestOptions: response.requestOptions,
          response: response,
          message: 'WebRTC SDP exchange failed with status ${response.statusCode}',
        );
      }

      final answer = response.data;
      if (answer is String) {
        return answer;
      }
      if (answer is List<int>) {
        return String.fromCharCodes(answer);
      }
      return answer?.toString() ?? '';
    } on DioException catch (error) {
      final status = error.response?.statusCode;
      final data = error.response?.data;
      logError(
        'Realtime SDP exchange failed (status: $status, body: $data)',
        error: error,
        stackTrace: error.stackTrace,
      );
      rethrow;
    }
  }

  Future<void> teardownSession() async {
    final session = _activeSession;
    if (session == null) return;
    try {
      await stopSession(session.sessionId);
    } finally {
      _activeSession = null;
    }
  }

  Future<void> stopSession(String sessionId) async {
    _sessions.remove(sessionId);
    // Azure realtime sessions do not require explicit teardown over HTTP.
    // Closing the PeerConnection/DataChannels is sufficient.
  }

  Stream<LatencyMetrics> observeServerMetrics() async* {
    yield const LatencyMetrics();
  }



  Future<String> translateText({
    required String sourceText,
    String targetLanguage = 'English',
  }) async {
    final config = AppConfig.instance;
    final endpoint = await _settingsStorage.getApiEndpoint();
    final responsesDeployment = await _settingsStorage.getResponsesDeployment();

    if (responsesDeployment.isEmpty) {
      throw StateError('Responses deployment is not configured.');
    }

    final normalized = AppConfig.normalizeEndpoint(endpoint);
    final baseUri = Uri.parse(normalized);
    final pathSegments = [
      ...baseUri.pathSegments.where((segment) => segment.isNotEmpty),
      'openai',
      'v1',
      'responses',
    ];
    final Map<String, String>? queryParameters = _responsesApiVersion.isEmpty
        ? null
        : {'api-version': _responsesApiVersion};
    final uri = baseUri.replace(
      pathSegments: pathSegments,
      queryParameters: queryParameters,
    );

    final prompt =
        'Translate the following utterance into $targetLanguage and reply using only the translated text:\n$sourceText';
    final body = {'model': responsesDeployment, 'input': prompt};

    logInfo('Dispatching translation request', {
      'uri': uri.toString(),
      'model': responsesDeployment,
      'targetLanguage': targetLanguage,
    });

    final Response<dynamic> response;
    try {
      response = await _dio.postUri(
        uri,
        data: body,
        options: Options(
          headers: {
            'api-key': config.apiKey,
            'Content-Type': 'application/json',
            'Accept': 'application/json',
          },
        ),
      );
    } on DioException catch (error) {
      if (error.response?.statusCode == 404) {
        logWarning('Translation deployment not found', {
          'uri': uri.toString(),
          'deployment': responsesDeployment,
          'status': error.response?.statusCode,
          'error': error.response?.data,
        });
      } else {
        logError(
          'Translation HTTP error',
          error: error,
          stackTrace: error.stackTrace,
        );
      }
      rethrow;
    }

    final data = response.data;
    if (data is Map<String, dynamic>) {
      final output = data['output'];
      if (output is List && output.isNotEmpty) {
        final first = output.first;
        if (first is Map<String, dynamic>) {
          final content = first['content'];
          if (content is List && content.isNotEmpty) {
            final textPart = content.firstWhere(
              (part) =>
                  part is Map &&
                  (part['type'] == 'output_text' || part['type'] == 'text'),
              orElse: () => null,
            );
            if (textPart is Map && textPart['text'] is String) {
              return textPart['text'] as String;
            }
          }
        }
      }
      if (data['output_text'] is String) {
        return data['output_text'] as String;
      }
    }
    throw StateError('Unexpected translation response format');
  }

  Uri _buildWebRtcUri(String baseUrl, String deployment) {
    // WebRTC URL 直接用 baseUrl，不拼接 deployment
    final base = Uri.parse(baseUrl.trim());
    return base;

  }

  /// 提取 region（如 eastus2）
  /// 确保 WebRTC URL 的区域与 Azure 资源区域一致，否则连接超时
  /// 参考：https://learn.microsoft.com/en-us/azure/ai-services/openai/realtime-audio
  String _extractRegionFromWebRtcUrl(String url) {
    final uri = Uri.parse(url);
    final host = uri.host;
    final region = host.split('.').first;
    return region;
  }


  Future<_RealtimePreferences> _loadPreferences() async {
    final transcriptionModel = await _settingsStorage
        .getRealtimeTranscriptionModel();
    final turnDetectionMode = await _settingsStorage.getTurnDetectionMode();
    final turnDetectionThreshold = await _settingsStorage
        .getTurnDetectionThreshold();
    final turnDetectionSilenceMs = await _settingsStorage
        .getTurnDetectionSilenceMs();
    final muteMicDuringPlayback = await _settingsStorage
        .getMuteMicDuringPlayback();

    final bool vadEnabled = turnDetectionMode != 'none';
    final double? sanitizedThreshold = vadEnabled
        ? ((turnDetectionThreshold ?? _defaultVadThreshold)
            .clamp(_minVadThreshold, _maxVadThreshold)
            .toDouble())
        : null;
    final int? sanitizedSilenceMs = vadEnabled
        ? ((turnDetectionSilenceMs ?? _defaultVadSilenceMs)
                .clamp(_minVadSilenceMs, _maxVadSilenceMs))
            .toInt()
        : null;

    return _RealtimePreferences(
      turnDetectionMode: turnDetectionMode,
      // Force enable transcription to avoid template responses when transcript is null
      transcriptionModel: transcriptionModel ?? 'gpt-4o-transcribe-diarize',
      turnDetectionThreshold: sanitizedThreshold,
      turnDetectionSilenceMs: sanitizedSilenceMs,
      muteMicDuringPlayback: muteMicDuringPlayback,
    );
  }
}

class _RealtimePreferences {
  const _RealtimePreferences({
    required this.turnDetectionMode,
    required this.transcriptionModel,
    required this.turnDetectionThreshold,
    required this.turnDetectionSilenceMs,
    required this.muteMicDuringPlayback,
  });

  final String turnDetectionMode;
  final String? transcriptionModel;
  final double? turnDetectionThreshold;
  final int? turnDetectionSilenceMs;
  final bool muteMicDuringPlayback;
}
