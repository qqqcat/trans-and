package com.example.translatorapp.data.repository

import android.util.Base64
import com.example.translatorapp.data.datasource.HistoryDao
import com.example.translatorapp.data.datasource.RealtimeSessionManager
import com.example.translatorapp.data.datasource.UserPreferencesDataSource
import com.example.translatorapp.data.model.TranslationHistoryEntity
import com.example.translatorapp.data.model.toEntity
import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.AccountSyncStatus
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.repository.TranslationRepository
import com.example.translatorapp.network.AccountHistoryItemDto
import com.example.translatorapp.network.AccountProfileRequest
import com.example.translatorapp.network.AccountSyncRequest
import com.example.translatorapp.network.ImageTranslationRequest
import com.example.translatorapp.network.RealtimeApi
import com.example.translatorapp.network.TextTranslationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val realtimeSessionManager: RealtimeSessionManager,
    private val historyDao: HistoryDao,
    private val preferencesDataSource: UserPreferencesDataSource,
    private val realtimeApi: RealtimeApi
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
        val direction = LanguageDirection(
            sourceLanguage = content.detectedSourceLanguage
                ?: currentSettings.direction.sourceLanguage,
            targetLanguage = content.targetLanguage ?: currentSettings.direction.targetLanguage
        )
        val entity = TranslationHistoryItem(
            id = 0,
            direction = direction,
            sourceText = content.transcript,
            translatedText = content.translation,
            createdAt = content.timestamp,
            inputMode = content.inputMode,
            detectedSourceLanguage = content.detectedSourceLanguage
        ).toEntity()
        historyDao.insert(entity)
    }

    override suspend fun clearHistory() {
        historyDao.clear()
    }

    override suspend fun refreshSettings(): UserSettings = preferencesDataSource.settings.first()

    override suspend fun translateText(
        text: String,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent {
        val response = realtimeApi.translateText(
            TextTranslationRequest(
                text = text,
                sourceLanguage = direction.sourceLanguage?.code,
                targetLanguage = direction.targetLanguage.code,
                model = profile.name
            )
        )
        val detected = response.detectedLanguage?.let { SupportedLanguage.fromCode(it) }
            ?: direction.sourceLanguage
        val target = response.targetLanguage?.let { SupportedLanguage.fromCode(it) }
            ?: direction.targetLanguage
        val content = TranslationContent(
            transcript = response.sourceText ?: text,
            translation = response.translation,
            timestamp = Clock.System.now(),
            detectedSourceLanguage = detected,
            targetLanguage = target,
            inputMode = TranslationInputMode.Text
        )
        persistHistoryItem(content)
        return content
    }

    override suspend fun translateImage(
        imageBytes: ByteArray,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent {
        val encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val response = realtimeApi.translateImage(
            ImageTranslationRequest(
                imageBase64 = encoded,
                sourceLanguage = direction.sourceLanguage?.code,
                targetLanguage = direction.targetLanguage.code,
                model = profile.name
            )
        )
        val detected = response.detectedLanguage?.let { SupportedLanguage.fromCode(it) }
            ?: direction.sourceLanguage
        val target = response.targetLanguage?.let { SupportedLanguage.fromCode(it) }
            ?: direction.targetLanguage
        val content = TranslationContent(
            transcript = response.sourceText ?: "",
            translation = response.translation,
            timestamp = Clock.System.now(),
            detectedSourceLanguage = detected,
            targetLanguage = target,
            inputMode = TranslationInputMode.Image
        )
        persistHistoryItem(content)
        return content
    }

    override suspend fun detectLanguage(text: String): SupportedLanguage? {
        if (text.isBlank()) return null
        return runCatching { realtimeApi.detectLanguage(text).language }
            .map { SupportedLanguage.fromCode(it) }
            .getOrNull()
    }

    override suspend fun updateHistoryFavorite(id: Long, isFavorite: Boolean) {
        historyDao.updateFavorite(id, isFavorite)
    }

    override suspend fun updateHistoryTags(id: Long, tags: Set<String>) {
        historyDao.updateTags(id, tags.joinToString(separator = ","))
    }

    override suspend fun syncAccount(): AccountSyncStatus {
        val settings = preferencesDataSource.settings.first()
        val accountId = settings.accountId
            ?: return AccountSyncStatus(success = false, message = "未绑定账号")
        if (!settings.syncEnabled) {
            return AccountSyncStatus(success = false, message = "同步已关闭")
        }
        val historyEntities: List<TranslationHistoryEntity> = historyDao.observeHistory().first()
        val payload = historyEntities.map { entity ->
            val item = entity.toDomain()
            AccountHistoryItemDto(
                id = item.id,
                sourceText = item.sourceText,
                translatedText = item.translatedText,
                direction = item.direction.encode(),
                inputMode = item.inputMode.name,
                detectedLanguage = item.detectedSourceLanguage?.code,
                tags = item.tags.toList(),
                isFavorite = item.isFavorite,
                createdAt = item.createdAt.toString()
            )
        }
        return runCatching {
            val response = realtimeApi.syncAccount(
                AccountSyncRequest(
                    accountId = accountId,
                    history = payload
                )
            )
            val syncedAt = runCatching { Instant.parse(response.syncedAt) }.getOrNull()
            val updatedSettings = settings.copy(lastSyncedAt = syncedAt)
            preferencesDataSource.update(updatedSettings)
            AccountSyncStatus(success = true, syncedAt = syncedAt)
        }.getOrElse { error ->
            AccountSyncStatus(success = false, message = error.message)
        }
    }

    override suspend fun updateAccountProfile(profile: AccountProfile) {
        val response = realtimeApi.updateAccountProfile(
            AccountProfileRequest(
                accountId = profile.accountId.ifBlank { null },
                email = profile.email,
                displayName = profile.displayName
            )
        )
        val current = preferencesDataSource.settings.first()
        preferencesDataSource.update(
            current.copy(
                accountId = response.accountId,
                accountEmail = response.email,
                accountDisplayName = response.displayName
            )
        )
    }

    override suspend fun updateSyncEnabled(enabled: Boolean) {
        val current = preferencesDataSource.settings.first()
        preferencesDataSource.update(current.copy(syncEnabled = enabled))
    }
}
