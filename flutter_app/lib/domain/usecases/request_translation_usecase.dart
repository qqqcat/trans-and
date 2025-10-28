import '../../data/repositories/translation_repository.dart';

class RequestTranslationUseCase {
  RequestTranslationUseCase(this._translationRepository);

  final TranslationRepository _translationRepository;

  Future<bool> call({
    required String sourceText,
    String targetLanguage = 'English',
  }) async {
    final entry = await _translationRepository.translateAndStore(
      sourceText: sourceText,
      targetLanguage: targetLanguage,
    );
    return entry != null;
  }
}
