import '../../data/repositories/session_repository.dart';

class InterruptAssistantUseCase {
  InterruptAssistantUseCase(this._repository);

  final SessionRepository _repository;

  void call() => _repository.interruptAssistant();
}