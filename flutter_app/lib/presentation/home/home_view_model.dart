import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/logging/logger.dart';
import '../../core/services/service_locator.dart';
import '../../data/repositories/settings_repository.dart';
import '../../domain/models/session_models.dart';
import '../../domain/usecases/interrupt_assistant_usecase.dart';
import '../../domain/usecases/observe_history_usecase.dart';
import '../../domain/usecases/start_session_usecase.dart';
import '../../domain/usecases/stop_session_usecase.dart';

final homeViewModelProvider = StateNotifierProvider<HomeViewModel, HomeState>((
  ref,
) {
  final sessionRepository = ref.read(sessionRepositoryProvider);
  final historyRepository = ref.read(historyRepositoryProvider);
  final settingsRepository = ref.read(settingsRepositoryProvider);

  return HomeViewModel(
    startSession: StartSessionUseCase(sessionRepository),
    stopSession: StopSessionUseCase(sessionRepository),
    interruptAssistant: InterruptAssistantUseCase(sessionRepository),
    observeHistory: ObserveHistoryUseCase(historyRepository),
    settingsRepository: settingsRepository,
  );
});

class HomeViewModel extends StateNotifier<HomeState> {
  HomeViewModel({
    required StartSessionUseCase startSession,
    required StopSessionUseCase stopSession,
    required InterruptAssistantUseCase interruptAssistant,
    required ObserveHistoryUseCase observeHistory,
    required SettingsRepository settingsRepository,
  }) : _startSession = startSession,
       _stopSession = stopSession,
       _interruptAssistant = interruptAssistant,
       _observeHistory = observeHistory,
       _settingsRepository = settingsRepository,
       super(const HomeState()) {
    _syncSelectedModel();
    _historySubscription = _observeHistory().listen((history) {
      state = state.copyWith(
        timeline: history.map(TimelineItem.fromHistoryEntry).toList(),
      );
    });
  }

  final StartSessionUseCase _startSession;
  final StopSessionUseCase _stopSession;
  final InterruptAssistantUseCase _interruptAssistant;
  final ObserveHistoryUseCase _observeHistory;
  final SettingsRepository _settingsRepository;
  late final StreamSubscription<List<HistoryEntry>> _historySubscription;

  @override
  void dispose() {
    _historySubscription.cancel();
    super.dispose();
  }

  Future<void> startSession() async {
    // Prevent starting session if already connecting or active
    if (state.sessionStatus == SessionStatus.connecting ||
        state.sessionStatus == SessionStatus.active) {
      logInfo('Session already connecting or active, ignoring start request');
      return;
    }

    try {
      state = state.copyWith(
        sessionStatus: SessionStatus.connecting,
        errorMessage: null,
      );
      await _startSession();
      final deployment = await _settingsRepository.getRealtimeDeployment();
      state = state.copyWith(
        sessionStatus: SessionStatus.active,
        modelLabel: deployment,
      );
      // 延后 HTTP 翻译请求，避免和 Realtime 初始化抢首屏资源
      // DISABLED: Temporarily disable REST translation for manual mode testing
      // Future.delayed(const Duration(seconds: 2), () async {
      //   final translated = await _requestTranslation(
      //     sourceText: '欢迎使用 TransAnd 实时翻译。',
      //     targetLanguage: 'English',
      //   );
      //   if (!translated) {
      //     state = state.copyWith(
      //       errorMessage:
      //         'Unable to reach the configured realtime deployment. Please review Settings.',
      //     );
      //   }
      // });
    } catch (error, stack) {
      logError('Failed to start session', error: error, stackTrace: stack);
      state = state.copyWith(
        sessionStatus: SessionStatus.idle,
        errorMessage: 'Unable to start realtime session',
      );
    }
  }

  Future<void> stopSession() async {
    try {
      await _stopSession();
    } catch (error, stack) {
      logError('Failed to stop session', error: error, stackTrace: stack);
    } finally {
      state = state.copyWith(sessionStatus: SessionStatus.idle);
    }
  }

  void interruptAssistant() {
    _interruptAssistant();
  }

  Future<void> _syncSelectedModel() async {
    try {
      final deployment = await _settingsRepository.getRealtimeDeployment();
      state = state.copyWith(modelLabel: deployment);
    } catch (error, stack) {
      logError(
        'Failed to load selected model',
        error: error,
        stackTrace: stack,
      );
    }
  }
}

class HomeState {
  const HomeState({
    this.sessionStatus = SessionStatus.idle,
    this.modelLabel = '',
    this.metrics = const LatencyMetrics(),
    this.timeline = const [],
    this.errorMessage,
  });

  final SessionStatus sessionStatus;
  final String modelLabel;
  final LatencyMetrics metrics;
  final List<TimelineItem> timeline;
  final String? errorMessage;

  HomeState copyWith({
    SessionStatus? sessionStatus,
    String? modelLabel,
    LatencyMetrics? metrics,
    List<TimelineItem>? timeline,
    String? errorMessage,
  }) {
    return HomeState(
      sessionStatus: sessionStatus ?? this.sessionStatus,
      modelLabel: modelLabel ?? this.modelLabel,
      metrics: metrics ?? this.metrics,
      timeline: timeline ?? this.timeline,
      errorMessage: errorMessage,
    );
  }
}

class TimelineItem {
  TimelineItem({
    required this.transcript,
    required this.translation,
    required this.timestampLabel,
  });

  final String transcript;
  final String translation;
  final String timestampLabel;

  factory TimelineItem.fromHistoryEntry(HistoryEntry entry) {
    return TimelineItem(
      transcript: entry.sourceText,
      translation: entry.targetText,
      timestampLabel: entry.timestampLabel,
    );
  }
}
