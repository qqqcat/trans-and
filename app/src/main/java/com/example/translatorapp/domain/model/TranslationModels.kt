package com.example.translatorapp.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.Flow

enum class LanguageDirection(val source: String, val target: String) {
    ChineseToFrench("zh-CN", "fr-FR"),
    FrenchToChinese("fr-FR", "zh-CN");

    fun reverse(): LanguageDirection = when (this) {
        ChineseToFrench -> FrenchToChinese
        FrenchToChinese -> ChineseToFrench
    }
}

enum class TranslationModelProfile(val displayName: String) {
    Balanced("GPT-4o mini"),
    Accuracy("GPT-4.1"),
    Offline("Whisper v3")
}

data class UserSettings(
    val direction: LanguageDirection = LanguageDirection.ChineseToFrench,
    val translationProfile: TranslationModelProfile = TranslationModelProfile.Balanced,
    val offlineFallbackEnabled: Boolean = true,
    val allowTelemetry: Boolean = false
)

data class TranslationContent(
    val transcript: String,
    val translation: String,
    val synthesizedAudioPath: String? = null,
    val timestamp: Instant = Clock.System.now(),
)

data class TranslationSessionState(
    val isActive: Boolean = false,
    val isMicrophoneOpen: Boolean = false,
    val latencyMetrics: LatencyMetrics = LatencyMetrics(),
    val direction: LanguageDirection = LanguageDirection.ChineseToFrench,
    val currentSegment: TranslationContent? = null,
    val errorMessage: String? = null,
)

data class LatencyMetrics(
    val asrLatencyMs: Long = 0,
    val translationLatencyMs: Long = 0,
    val ttsLatencyMs: Long = 0,
)

data class TranslationHistoryItem(
    val id: Long,
    val direction: LanguageDirection,
    val sourceText: String,
    val translatedText: String,
    val createdAt: Instant
)

interface TranslationSession {
    val state: Flow<TranslationSessionState>
    val transcriptStream: Flow<TranslationContent>

    suspend fun start(settings: UserSettings)
    suspend fun stop()
    suspend fun sendTextPrompt(prompt: String)
}
