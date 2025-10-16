package com.example.translatorapp.data.repository

import com.example.translatorapp.data.datasource.HistoryDao
import com.example.translatorapp.data.datasource.RealtimeSessionManager
import com.example.translatorapp.data.datasource.UserPreferencesDataSource
import com.example.translatorapp.data.model.toEntity
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.repository.TranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val realtimeSessionManager: RealtimeSessionManager,
    private val historyDao: HistoryDao,
    private val preferencesDataSource: UserPreferencesDataSource
) : TranslationRepository {

    override val sessionState: Flow<TranslationSessionState> = realtimeSessionManager.state

    override val liveTranscription: Flow<TranslationContent> = realtimeSessionManager.transcriptStream

    override val history: Flow<List<TranslationHistoryItem>> =
        historyDao.observeHistory().map { entities -> entities.map { it.toDomain() } }

    override suspend fun startRealtimeSession(settings: UserSettings) {
        preferencesDataSource.update(settings)
        realtimeSessionManager.start(settings)
    }

    override suspend fun stopRealtimeSession() {
        realtimeSessionManager.stop()
    }

    override suspend fun toggleMicrophone(): Boolean = realtimeSessionManager.toggleMicrophone()

    override suspend fun updateDirection(direction: LanguageDirection) {
        val current = preferencesDataSource.settings.first()
        val updated = current.copy(direction = direction)
        preferencesDataSource.update(updated)
        realtimeSessionManager.updateDirection(direction)
    }

    override suspend fun updateModel(profile: TranslationModelProfile) {
        val current = preferencesDataSource.settings.first()
        val updated = current.copy(translationProfile = profile)
        preferencesDataSource.update(updated)
        realtimeSessionManager.updateModel(profile)
    }

    override suspend fun updateOfflineFallback(enabled: Boolean) {
        val current = preferencesDataSource.settings.first()
        preferencesDataSource.update(current.copy(offlineFallbackEnabled = enabled))
    }

    override suspend fun updateTelemetryConsent(consent: Boolean) {
        val current = preferencesDataSource.settings.first()
        preferencesDataSource.update(current.copy(allowTelemetry = consent))
    }

    override suspend fun persistHistoryItem(content: TranslationContent) {
        val currentSettings = preferencesDataSource.settings.first()
        val entity = TranslationHistoryItem(
            id = 0,
            direction = currentSettings.direction,
            sourceText = content.transcript,
            translatedText = content.translation,
            createdAt = content.timestamp
        ).toEntity()
        historyDao.insert(entity)
    }

    override suspend fun clearHistory() {
        historyDao.clear()
    }

    override suspend fun refreshSettings(): UserSettings = preferencesDataSource.settings.first()
}
