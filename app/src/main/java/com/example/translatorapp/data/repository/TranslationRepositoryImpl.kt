package com.example.translatorapp.data.repository

import android.util.Base64
import com.example.translatorapp.data.datasource.HistoryDao
import com.example.translatorapp.data.datasource.RealtimeSessionManager
import com.example.translatorapp.data.datasource.UserPreferencesDataSource
import com.example.translatorapp.data.model.TranslationHistoryEntity
import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.AccountSyncStatus
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.TranslatorException
import com.example.translatorapp.domain.model.UiAction
import com.example.translatorapp.domain.model.UiMessageLevel
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.repository.TranslationRepository
import com.example.translatorapp.network.AccountHistoryItemDto
import com.example.translatorapp.network.AccountProfileRequest
import com.example.translatorapp.network.AccountSyncRequest
import com.example.translatorapp.network.ApiConfig
import com.example.translatorapp.network.ImageTranslationRequest
import com.example.translatorapp.network.RealtimeApi
import com.example.translatorapp.network.RealtimeApiFactory
import com.example.translatorapp.network.TextTranslationRequest
import com.example.translatorapp.network.ensureTrailingSlash
import com.example.translatorapp.util.DispatcherProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import retrofit2.HttpException

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val realtimeSessionManager: RealtimeSessionManager,
    private val historyDao: HistoryDao,
    private val preferencesDataSource: UserPreferencesDataSource,
    private val apiFactory: RealtimeApiFactory,
    private val apiConfig: ApiConfig,
    private val dispatcherProvider: DispatcherProvider
) : TranslationRepository {

    @Volatile
    private var cachedApi: Pair<String, RealtimeApi>? = null

    override val sessionState: Flow<TranslationSessionState> = realtimeSessionManager.state

    override val liveTranscription: Flow<TranslationContent> = realtimeSessionManager.transcriptStream

    override val history: Flow<List<TranslationHistoryItem>> =
        historyDao.observeHistory().map { entities ->
            entities.map(TranslationHistoryEntity::toDomain)
        }

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
        preferencesDataSource.update(current.copy(direction = direction))
        realtimeSessionManager.updateDirection(direction)
    }

    override suspend fun updateModel(profile: TranslationModelProfile) {
        val current = preferencesDataSource.settings.first()
        preferencesDataSource.update(current.copy(translationProfile = profile))
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
        val fallbackSettings = preferencesDataSource.settings.first()
        withContext(dispatcherProvider.io) {
            historyDao.insert(content.toHistoryEntity(fallbackSettings.direction))
        }
    }

    override suspend fun clearHistory() {
        withContext(dispatcherProvider.io) {
            historyDao.clear()
        }
    }

    override suspend fun refreshSettings(): UserSettings = preferencesDataSource.settings.first()

    override suspend fun translateText(
        text: String,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent {
        val response = runWithApi { api ->
            api.translateText(
                TextTranslationRequest(
                    text = text,
                    sourceLanguage = direction.sourceLanguage?.code,
                    targetLanguage = direction.targetLanguage.code,
                    model = profile.name
                )
            )
        }
        val detected = response.detectedLanguage?.let(SupportedLanguage::fromCode) ?: direction.sourceLanguage
        val target = response.targetLanguage?.let(SupportedLanguage::fromCode) ?: direction.targetLanguage
        val content = TranslationContent(
            transcript = response.sourceText ?: text,
            translation = response.translation,
            detectedSourceLanguage = detected,
            targetLanguage = target,
            timestamp = Clock.System.now(),
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
        val response = runWithApi { api ->
            api.translateImage(
                ImageTranslationRequest(
                    imageBase64 = encoded,
                    sourceLanguage = direction.sourceLanguage?.code,
                    targetLanguage = direction.targetLanguage.code,
                    model = profile.name
                )
            )
        }
        val detected = response.detectedLanguage?.let(SupportedLanguage::fromCode) ?: direction.sourceLanguage
        val target = response.targetLanguage?.let(SupportedLanguage::fromCode) ?: direction.targetLanguage
        val content = TranslationContent(
            transcript = response.sourceText.orEmpty(),
            translation = response.translation,
            detectedSourceLanguage = detected,
            targetLanguage = target,
            timestamp = Clock.System.now(),
            inputMode = TranslationInputMode.Image
        )
        persistHistoryItem(content)
        return content
    }

    override suspend fun detectLanguage(text: String): SupportedLanguage? {
        if (text.isBlank()) return null
        val response = runWithApi { api -> api.detectLanguage(text) }
        return SupportedLanguage.fromCode(response.language)
    }

    override suspend fun updateHistoryFavorite(id: Long, isFavorite: Boolean) {
        withContext(dispatcherProvider.io) {
            historyDao.updateFavorite(id, isFavorite)
        }
    }

    override suspend fun updateHistoryTags(id: Long, tags: Set<String>) {
        withContext(dispatcherProvider.io) {
            historyDao.updateTags(id, tags.joinToString(separator = ","))
        }
    }

    override suspend fun syncAccount(): AccountSyncStatus {
        val settings = preferencesDataSource.settings.first()
        val accountId = settings.accountId ?: return AccountSyncStatus(
            success = false,
            message = "尚未绑定账号"
        )
        if (!settings.syncEnabled) {
            return AccountSyncStatus(success = false, message = "同步已关闭")
        }
        val entities = withContext(dispatcherProvider.io) {
            historyDao.observeHistory().first()
        }
        val payload = entities.map { entity ->
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
        return try {
            val response = runWithApi { api ->
                api.syncAccount(
                    AccountSyncRequest(
                        accountId = accountId,
                        history = payload
                    )
                )
            }
            val syncedAt = runCatching { Instant.parse(response.syncedAt) }.getOrNull()
            preferencesDataSource.update(settings.copy(lastSyncedAt = syncedAt))
            AccountSyncStatus(success = true, syncedAt = syncedAt)
        } catch (error: TranslatorException) {
            AccountSyncStatus(success = false, message = error.userMessage)
        }
    }

    override suspend fun updateAccountProfile(profile: AccountProfile) {
        val response = runWithApi { api ->
            api.updateAccountProfile(
                AccountProfileRequest(
                    accountId = profile.accountId.ifBlank { null },
                    email = profile.email,
                    displayName = profile.displayName
                )
            )
        }
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

    override suspend fun updateApiEndpoint(endpoint: String) {
        val normalized = endpoint.trim().trimEnd('/')
        val current = preferencesDataSource.settings.first()
        if (current.apiEndpoint == normalized) {
            return
        }
        preferencesDataSource.update(current.copy(apiEndpoint = normalized))
        cachedApi = null
        realtimeSessionManager.stop()
    }

    private suspend fun <T> runWithApi(block: suspend (RealtimeApi) -> T): T = block(retrieveApi())

    private suspend fun retrieveApi(): RealtimeApi {
        val settings = preferencesDataSource.settings.first()
        val baseUrl = (settings.apiEndpoint.takeIf { it.isNotBlank() } ?: apiConfig.baseUrl).ensureTrailingSlash()
        val cached = cachedApi
        if (cached != null && cached.first == baseUrl) {
            return cached.second
        }
        val fresh = try {
            apiFactory.create(baseUrl)
        } catch (throwable: Throwable) {
            throw TranslatorException(
                userMessage = "API Host 无效或不可用，请在设置页确认输入是否正确",
                action = UiAction.OpenSettings,
                cause = throwable
            )
        }
        cachedApi = baseUrl to fresh
        return fresh
    }

    private fun TranslationContent.toHistoryEntity(fallbackDirection: LanguageDirection): TranslationHistoryEntity {
        val direction = LanguageDirection(
            sourceLanguage = detectedSourceLanguage ?: fallbackDirection.sourceLanguage,
            targetLanguage = targetLanguage ?: fallbackDirection.targetLanguage
        )
        val item = TranslationHistoryItem(
            id = 0,
            direction = direction,
            sourceText = transcript,
            translatedText = translation,
            createdAt = timestamp,
            inputMode = inputMode,
            detectedSourceLanguage = detectedSourceLanguage,
            isFavorite = false,
            tags = emptySet()
        )
        return TranslationHistoryEntity.fromDomain(item)
    }

    private fun Throwable.toTranslatorException(): TranslatorException = when (this) {
        is TranslatorException -> this
        is IOException -> TranslatorException(
            userMessage = "网络连接异常，请检查网络或代理设置",
            action = UiAction.Retry,
            cause = this
        )
        is HttpException -> this.toTranslatorException()
        else -> TranslatorException(
            userMessage = this.message ?: "发生未知错误，请稍后再试",
            action = UiAction.Retry,
            cause = this
        )
    }

    private fun HttpException.toTranslatorException(): TranslatorException {
        val action = when (code()) {
            401, 403 -> UiAction.OpenSettings
            404 -> UiAction.OpenSettings
            else -> UiAction.Retry
        }
        val errorBody = runCatching { response()?.errorBody()?.string() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        val baseMessage = when (code()) {
            401 -> "认证失败，请检查账户或 API Key 设置"
            403 -> "没有访问权限，请确认账号权限或额度"
            404 -> "服务不可达，请检查 API Host 配置"
            429 -> "请求过于频繁，请稍后重试"
            else -> "服务响应异常（HTTP ${code()}）"
        }
        val message = errorBody ?: baseMessage
        return TranslatorException(
            userMessage = message,
            action = action,
            level = UiMessageLevel.Error,
            cause = this
        )
    }
}
