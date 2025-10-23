package com.example.translatorapp.domain.usecase

import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.AccountSyncStatus
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.ThemeMode
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.repository.TranslationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartRealtimeSessionUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(settings: UserSettings) = repository.startRealtimeSession(settings)
}

class StopRealtimeSessionUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke() = repository.stopRealtimeSession()
}

class ToggleMicrophoneUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(): Boolean = repository.toggleMicrophone()
}

class UpdateDirectionUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(direction: LanguageDirection) = repository.updateDirection(direction)
}

class UpdateModelUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(profile: TranslationModelProfile) = repository.updateModel(profile)
}

class UpdateTelemetryConsentUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(consent: Boolean) = repository.updateTelemetryConsent(consent)
}

class ObserveSessionStateUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    operator fun invoke(): Flow<TranslationSessionState> = repository.sessionState
}

class ObserveLiveTranscriptionUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    operator fun invoke(): Flow<TranslationContent> = repository.liveTranscription
}

class ObserveHistoryUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    operator fun invoke(): Flow<List<TranslationHistoryItem>> = repository.history
}

class PersistHistoryUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(content: TranslationContent) = repository.persistHistoryItem(content)
}

class ClearHistoryUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke() = repository.clearHistory()
}

class LoadSettingsUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(): UserSettings = repository.refreshSettings()
}

class ObserveSettingsUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    operator fun invoke(): Flow<UserSettings> = repository.settings
}

class UpdateThemeModeUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(themeMode: ThemeMode) = repository.updateThemeMode(themeMode)
}

class UpdateAppLanguageUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(language: String?) = repository.updateAppLanguage(language)
}

class TranslateTextUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(
        text: String,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent = repository.translateText(text, direction, profile)
}

class TranslateImageUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(
        imageBytes: ByteArray,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent = repository.translateImage(imageBytes, direction, profile)
}

class DetectLanguageUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(text: String): SupportedLanguage? = repository.detectLanguage(text)
}

class UpdateHistoryFavoriteUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(id: Long, favorite: Boolean) = repository.updateHistoryFavorite(id, favorite)
}

class UpdateHistoryTagsUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(id: Long, tags: Set<String>) = repository.updateHistoryTags(id, tags)
}

class SyncAccountUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(): AccountSyncStatus = repository.syncAccount()
}

class UpdateAccountProfileUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(profile: AccountProfile) = repository.updateAccountProfile(profile)
}

class UpdateSyncEnabledUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(enabled: Boolean) = repository.updateSyncEnabled(enabled)
}

class UpdateApiEndpointUseCase @Inject constructor(
    private val repository: TranslationRepository
) {
    suspend operator fun invoke(endpoint: String) = repository.updateApiEndpoint(endpoint)
}

