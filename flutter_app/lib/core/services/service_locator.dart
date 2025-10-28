import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/repositories/history_repository.dart';
import '../../data/repositories/session_repository.dart';
import '../../data/repositories/translation_repository.dart';
import '../../data/repositories/settings_repository.dart';
import '../../services/audio/audio_session_service.dart';
import '../../services/realtime/realtime_api_client.dart';
import '../../services/webrtc/webrtc_service.dart';

final realtimeApiClientProvider = Provider<RealtimeApiClient>(
  (ref) => RealtimeApiClient(),
);

final webRtcServiceProvider = Provider<WebRtcService>(
  (ref) =>
      WebRtcService(realtimeApiClient: ref.read(realtimeApiClientProvider)),
);

final audioSessionServiceProvider = Provider<AudioSessionService>(
  (ref) => AudioSessionService(),
);

final sessionRepositoryProvider = Provider<SessionRepository>(
  (ref) => SessionRepository(
    realtimeApiClient: ref.read(realtimeApiClientProvider),
    webRtcService: ref.read(webRtcServiceProvider),
    audioSessionService: ref.read(audioSessionServiceProvider),
  ),
);

final historyRepositoryProvider = Provider<HistoryRepository>(
  (ref) => HistoryRepository(),
);

final settingsRepositoryProvider = Provider<SettingsRepository>(
  (ref) => SettingsRepository(),
);

final translationRepositoryProvider = Provider<TranslationRepository>(
  (ref) => TranslationRepository(
    realtimeApiClient: ref.read(realtimeApiClientProvider),
    historyRepository: ref.read(historyRepositoryProvider),
  ),
);

Future<void> configureServices() async {
  // Placeholder for offline database initialization etc.
}
