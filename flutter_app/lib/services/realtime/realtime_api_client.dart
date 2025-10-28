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
  });

  final String sessionId;
  final String ephemeralKey;
  final String deployment;
  final Uri resourceEndpoint;
  final Uri webrtcEndpoint;
  final List<IceServer> iceServers;
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
      _dio = dio ?? Dio();

  static const _previewApiVersion = '2025-04-01-preview';
  static const _responsesApiVersion = '2024-08-01-preview';
  static const _defaultRealtimeVoice = 'verse';

  final SettingsStorage _settingsStorage;
  final Dio _dio;

  final Map<String, RealtimeSession> _sessions = {};
  RealtimeSession? _activeSession;

  bool get _isPreview => true;

  Future<RealtimeSession> createSession() async {
    final config = AppConfig.instance;
    final endpoint = await _settingsStorage.getApiEndpoint();
    final realtimeDeployment = await _settingsStorage.getRealtimeDeployment();
    final webRtcBaseUrl = await _settingsStorage.getWebRtcUrl();

    if (realtimeDeployment.isEmpty) {
      throw StateError('Realtime deployment is not configured.');
    }
    if (webRtcBaseUrl.isEmpty) {
      throw StateError('Realtime WebRTC URL is not configured.');
    }

    final uri = _buildStartSessionUri(endpoint, realtimeDeployment);
    final payload = _isPreview
        ? <String, dynamic>{
            'model': realtimeDeployment,
            'voice': _defaultRealtimeVoice,
          }
        : <String, dynamic>{
            'session': {
              'instructions': 'You are a realtime interpreter.',
              'modalities': ['text', 'audio'],
              'voice': _defaultRealtimeVoice,
              'input_audio_format': 'pcm16',
              'output_audio_format': 'pcm16',
            },
          };

    logInfo('Creating realtime session', {
      'uri': uri.toString(),
      'deployment': realtimeDeployment,
    });

    final response = await _dio.postUri(
      uri,
      data: payload,
      options: Options(
        headers: {
          'api-key': config.apiKey,
          'Content-Type': 'application/json',
          'Accept': 'application/json',
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
    );

    _sessions[sessionId] = realtimeSession;
    _activeSession = realtimeSession;
    logInfo('Realtime session issued', {
      'sessionId': sessionId,
      'webrtcEndpoint': realtimeSession.webrtcEndpoint.toString(),
      'ephemeralKeyPrefix': clientSecret.length <= 8
          ? clientSecret
          : '${clientSecret.substring(0, 4)}...${clientSecret.substring(clientSecret.length - 4)}',
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

    final response = await _dio.postUri(
      session.webrtcEndpoint,
      data: offerSdp,
      options: Options(
        headers: {
          'Authorization': 'Bearer ${session.ephemeralKey}',
          'Accept': 'application/sdp',
        },
        contentType: 'application/sdp',
        responseType: ResponseType.plain,
      ),
    );

    final answer = response.data;
    if (answer is String) {
      return answer;
    }
    if (answer is List<int>) {
      return String.fromCharCodes(answer);
    }
    return answer?.toString() ?? '';
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

  Uri _buildStartSessionUri(String endpoint, String deployment) {
    return _buildSessionsCollectionUri(endpoint, deployment: deployment);
  }

  Uri _buildSessionsCollectionUri(
    String endpoint, {
    required String deployment,
  }) {
    final normalized = AppConfig.normalizeEndpoint(endpoint);
    final base = Uri.parse(normalized);
    final baseSegments = base.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList();
    final commonSegments = _isPreview
        ? ['openai', 'realtimeapi', 'sessions']
        : ['openai', 'v1', 'realtime', 'sessions'];
    final query = _isPreview
        ? {'api-version': _previewApiVersion, 'deployment': deployment}
        : {'model': deployment};
    return base.replace(
      pathSegments: [...baseSegments, ...commonSegments],
      queryParameters: query,
    );
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
      'deployments',
      responsesDeployment,
      'responses',
    ];
    final queryParameters = <String, String>{
      'api-version': _responsesApiVersion,
    };
    final uri = baseUri.replace(
      pathSegments: pathSegments,
      queryParameters: queryParameters,
    );

    final body = {
      'model': responsesDeployment,
      'input': [
        {
          'role': 'system',
          'content': [
            {
              'type': 'text',
              'text':
                  'You are a professional simultaneous interpreter. Translate user utterances into $targetLanguage concisely. Reply using only the translated text.',
            },
          ],
        },
        {
          'role': 'user',
          'content': [
            {'type': 'input_text', 'text': sourceText},
          ],
        },
      ],
    };

    logInfo('Dispatching translation request', {
      'uri': uri.toString(),
      'deployment': responsesDeployment,
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
    final base = Uri.parse(baseUrl.trim());
    final query = Map<String, String>.from(base.queryParameters)
      ..['model'] = deployment;
    return base.replace(queryParameters: query.isEmpty ? null : query);
  }
}
