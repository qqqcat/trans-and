import '../../data/repositories/history_repository.dart';
import '../../domain/models/session_models.dart';

class ObserveHistoryUseCase {
  ObserveHistoryUseCase(this._repository);

  final HistoryRepository _repository;

  Stream<List<HistoryEntry>> call() => _repository.observeHistory();
}
