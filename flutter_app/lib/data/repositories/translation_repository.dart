import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

import '../../core/logging/logger.dart';
import '../../domain/models/session_models.dart';
import '../../services/realtime/realtime_api_client.dart';
import '../repositories/history_repository.dart';

class TranslationRepository {
  TranslationRepository({
    required RealtimeApiClient realtimeApiClient,
    required HistoryRepository historyRepository,
  }) : _realtimeApiClient = realtimeApiClient,
       _historyRepository = historyRepository;

  final RealtimeApiClient _realtimeApiClient;
  final HistoryRepository _historyRepository;
  final _uuid = const Uuid();

  Future<HistoryEntry?> translateAndStore({
    required String sourceText,
    String targetLanguage = 'English',
  }) async {
    try {
      final result = await _realtimeApiClient.translateText(
        sourceText: sourceText,
        targetLanguage: targetLanguage,
      );

      final entry = HistoryEntry(
        id: _uuid.v4(),
        sourceText: sourceText,
        targetText: result,
        timestamp: DateTime.now(),
      );
      await _historyRepository.addEntry(entry);
      return entry;
    } on DioException catch (error, stack) {
      final status = error.response?.statusCode;
      logError(
        'Translation request failed',
        error: error.response?.data ?? error.message,
        stackTrace: stack,
      );
      if (status == 404) {
        logWarning('Translation deployment not found for responses request', {
          'status': status,
        });
      }
      return null;
    } catch (error, stack) {
      logError('Translation request failed', error: error, stackTrace: stack);
      rethrow;
    }
  }
}
