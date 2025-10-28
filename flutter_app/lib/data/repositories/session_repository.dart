import '../../core/logging/logger.dart';
import '../../domain/models/session_models.dart';
import '../../services/audio/audio_session_service.dart';
import '../../services/realtime/realtime_api_client.dart';
import '../../services/webrtc/webrtc_service.dart';

class SessionRepository {
  SessionRepository({
    required RealtimeApiClient realtimeApiClient,
    required WebRtcService webRtcService,
    required AudioSessionService audioSessionService,
  }) : _realtimeApiClient = realtimeApiClient,
       _webRtcService = webRtcService,
       _audioSessionService = audioSessionService;

  final RealtimeApiClient _realtimeApiClient;
  final WebRtcService _webRtcService;
  final AudioSessionService _audioSessionService;

  Future<void> startSession() async {
    await _audioSessionService.startCapture();
    try {
      final session = await _realtimeApiClient.createSession();
      await _webRtcService.connect(session);
    } catch (error, stack) {
      logWarning(
        'Failed to initialize realtime session; continuing with degraded mode',
        {'error': error.toString(), 'stackTrace': stack.toString()},
      );
    }
  }

  Future<void> stopSession() async {
    await _audioSessionService.stopCapture();
    await _webRtcService.disconnect();
    await _realtimeApiClient.teardownSession();
  }

  Stream<LatencyMetrics> observeMetrics() => _webRtcService.metricsStream;
}
