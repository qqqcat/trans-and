package com.example.translatorapp.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.Flow

private const val LANGUAGE_DIRECTION_SEPARATOR = "->"

data class Language(
    val code: String,
    val displayName: String
)

data class LanguageDirection(
    val source: Language,
    val target: Language
) {
    val id: String = buildId(source.code, target.code)
    val displayName: String get() = "${source.displayName} → ${target.displayName}"

    fun reverse(): LanguageDirection = LanguageDirection(target, source)

    companion object {
        fun buildId(sourceCode: String, targetCode: String): String =
            "$sourceCode$LANGUAGE_DIRECTION_SEPARATOR$targetCode"

        fun fromId(id: String): LanguageDirection? {
            val parts = id.split(LANGUAGE_DIRECTION_SEPARATOR)
            if (parts.size != 2) return null
            val (sourceCode, targetCode) = parts
            val source = LanguageCatalog.findLanguage(sourceCode) ?: return null
            val target = LanguageCatalog.findLanguage(targetCode) ?: return null
            if (source == target) return null
            return LanguageDirection(source, target)
        }
    }
}

object LanguageCatalog {
    val languages: List<Language> = listOf(
        Language(code = "zh-CN", displayName = "中文"),
        Language(code = "fr-FR", displayName = "法语"),
        Language(code = "en-US", displayName = "英语"),
        Language(code = "es-ES", displayName = "西班牙语"),
        Language(code = "de-DE", displayName = "德语"),
        Language(code = "ja-JP", displayName = "日语")
    )

    val defaultDirection: LanguageDirection = LanguageDirection(
        source = languages.first { it.code == "zh-CN" },
        target = languages.first { it.code == "fr-FR" }
    )

    val directions: List<LanguageDirection> = languages.flatMap { source ->
        languages.filter { target -> target != source }.map { target ->
            LanguageDirection(source, target)
        }
    }

    fun findLanguage(code: String): Language? = languages.find { it.code == code }

    fun findDirection(id: String): LanguageDirection? = LanguageDirection.fromId(id)
}

enum class TranslationModelProfile(val displayName: String) {
    Balanced("GPT-4o mini"),
    Accuracy("GPT-4.1"),
    Offline("Whisper v3")
}

data class UserSettings(
    val direction: LanguageDirection = LanguageCatalog.defaultDirection,
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
    val direction: LanguageDirection = LanguageCatalog.defaultDirection,
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
