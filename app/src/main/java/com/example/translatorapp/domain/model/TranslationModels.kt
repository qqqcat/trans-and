package com.example.translatorapp.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private const val AUTO_DETECT_LANGUAGE_CODE = "auto"

enum class SupportedLanguage(val code: String, val displayName: String) {
    ChineseSimplified("zh-CN", "简体中文"),
    English("en-US", "English"),
    French("fr-FR", "Français"),
    Spanish("es-ES", "Español"),
    German("de-DE", "Deutsch"),
    Japanese("ja-JP", "日本語"),
    Korean("ko-KR", "한국어"),
    Portuguese("pt-BR", "Português"),
    Russian("ru-RU", "Русский"),
    Arabic("ar-SA", "العربية");

    companion object {
        fun fromCode(code: String?): SupportedLanguage? = entries.firstOrNull { it.code == code }
    }
}

data class LanguageDirection(
    val sourceLanguage: SupportedLanguage?,
    val targetLanguage: SupportedLanguage,
) {
    val isAutoDetect: Boolean
        get() = sourceLanguage == null

    fun encode(): String = "${sourceLanguage?.code ?: AUTO_DETECT_LANGUAGE_CODE}|${targetLanguage.code}"

    fun withSource(language: SupportedLanguage?): LanguageDirection = copy(sourceLanguage = language)

    fun withTarget(language: SupportedLanguage): LanguageDirection = copy(targetLanguage = language)

    companion object {
        fun decode(value: String?): LanguageDirection {
            if (value.isNullOrBlank()) {
                return LanguageDirection(
                    sourceLanguage = SupportedLanguage.ChineseSimplified,
                    targetLanguage = SupportedLanguage.English
                )
            }
            return when {
                value.contains("|") -> {
                    val (source, target) = value.split("|", limit = 2).let {
                        it.firstOrNull() to it.getOrNull(1)
                    }
                    val sourceLanguage = if (source.isNullOrBlank() || source == AUTO_DETECT_LANGUAGE_CODE) {
                        null
                    } else {
                        SupportedLanguage.fromCode(source)
                    }
                    val targetLanguage = SupportedLanguage.fromCode(target)
                        ?: SupportedLanguage.English
                    LanguageDirection(sourceLanguage, targetLanguage)
                }

                value == "ChineseToFrench" -> LanguageDirection(
                    SupportedLanguage.ChineseSimplified,
                    SupportedLanguage.French
                )

                value == "FrenchToChinese" -> LanguageDirection(
                    SupportedLanguage.French,
                    SupportedLanguage.ChineseSimplified
                )

                else -> LanguageDirection(
                    sourceLanguage = SupportedLanguage.fromCode(value),
                    targetLanguage = SupportedLanguage.English
                )
            }
        }
    }
}

enum class TranslationModelProfile(val displayName: String) {
    Balanced("GPT-4o mini"),
    Accuracy("GPT-4.1"),
    Offline("Whisper v3")
}

enum class TranslationInputMode {
    Voice,
    Text,
    Image,
}

data class UserSettings(
    val direction: LanguageDirection = LanguageDirection(
        SupportedLanguage.ChineseSimplified,
        SupportedLanguage.English
    ),
    val translationProfile: TranslationModelProfile = TranslationModelProfile.Balanced,
    val offlineFallbackEnabled: Boolean = true,
    val allowTelemetry: Boolean = false,
    val syncEnabled: Boolean = true,
    val accountId: String? = null,
    val accountEmail: String? = null,
    val accountDisplayName: String? = null,
    val lastSyncedAt: Instant? = null,
)

data class TranslationContent(
    val transcript: String,
    val translation: String,
    val synthesizedAudioPath: String? = null,
    val timestamp: Instant = Clock.System.now(),
    val detectedSourceLanguage: SupportedLanguage? = null,
    val targetLanguage: SupportedLanguage? = null,
    val inputMode: TranslationInputMode = TranslationInputMode.Voice,
)

data class TranslationSessionState(
    val isActive: Boolean = false,
    val isMicrophoneOpen: Boolean = false,
    val latencyMetrics: LatencyMetrics = LatencyMetrics(),
    val direction: LanguageDirection = LanguageDirection(
        SupportedLanguage.ChineseSimplified,
        SupportedLanguage.English
    ),
    val availableInputModes: Set<TranslationInputMode> = TranslationInputMode.entries.toSet(),
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
    val createdAt: Instant,
    val inputMode: TranslationInputMode = TranslationInputMode.Voice,
    val detectedSourceLanguage: SupportedLanguage? = null,
    val isFavorite: Boolean = false,
    val tags: Set<String> = emptySet(),
)

data class AccountProfile(
    val accountId: String,
    val email: String,
    val displayName: String? = null,
    val lastSyncedAt: Instant? = null,
)

data class AccountSyncStatus(
    val success: Boolean,
    val message: String? = null,
    val syncedAt: Instant? = null,
)

interface TranslationSession {
    val state: Flow<TranslationSessionState>
    val transcriptStream: Flow<TranslationContent>

    suspend fun start(settings: UserSettings)
    suspend fun stop()
    suspend fun sendTextPrompt(prompt: String)
}
