package com.example.translatorapp.domain.repository

import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.AccountSyncStatus
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface TranslationRepository {
    val sessionState: Flow<TranslationSessionState>
    val liveTranscription: Flow<TranslationContent>
    val history: Flow<List<TranslationHistoryItem>>

    suspend fun startRealtimeSession(settings: UserSettings)
    suspend fun stopRealtimeSession()
    suspend fun toggleMicrophone(): Boolean
    suspend fun updateDirection(direction: LanguageDirection)
    suspend fun updateModel(profile: TranslationModelProfile)
    suspend fun updateOfflineFallback(enabled: Boolean)
    suspend fun updateTelemetryConsent(consent: Boolean)
    suspend fun persistHistoryItem(content: TranslationContent)
    suspend fun clearHistory()
    suspend fun refreshSettings(): UserSettings
    suspend fun translateText(
        text: String,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent

    suspend fun translateImage(
        imageBytes: ByteArray,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent

    suspend fun detectLanguage(text: String): SupportedLanguage?
    suspend fun updateHistoryFavorite(id: Long, isFavorite: Boolean)
    suspend fun updateHistoryTags(id: Long, tags: Set<String>)
    suspend fun syncAccount(): AccountSyncStatus
    suspend fun updateAccountProfile(profile: AccountProfile)
    suspend fun updateSyncEnabled(enabled: Boolean)
    suspend fun updateApiEndpoint(endpoint: String)
}
