import '../../data/repositories/session_repository.dart';

class StopSessionUseCase {
  StopSessionUseCase(this._repository);

  final SessionRepository _repository;

  Future<void> call() => _repository.stopSession();
}
