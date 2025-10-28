import '../../data/repositories/session_repository.dart';

class StartSessionUseCase {
  StartSessionUseCase(this._repository);

  final SessionRepository _repository;

  Future<void> call() => _repository.startSession();
}
