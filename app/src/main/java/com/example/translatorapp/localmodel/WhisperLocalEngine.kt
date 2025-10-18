package com.example.translatorapp.localmodel

import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.offline.OfflineModelProfile
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

data class WhisperRequest(
    val sampleRate: Int,
    val sourceLanguage: SupportedLanguage?,
    val targetLanguage: SupportedLanguage,
    val enableTranslation: Boolean,
    val modelProfile: OfflineModelProfile
)

data class WhisperResult(
    val transcript: String,
    val translation: String? = null,
    val detectedLanguage: SupportedLanguage? = null
)

interface LocalSpeechRecognizer {
    suspend fun transcribe(audio: ByteArray, request: WhisperRequest): WhisperResult
}

@Singleton
class WhisperLocalEngine @Inject constructor() : LocalSpeechRecognizer {
    override suspend fun transcribe(audio: ByteArray, request: WhisperRequest): WhisperResult {
        delay(50)
        val transcript = "[stub] offline transcription"
        val translation = if (request.enableTranslation) {
            "[stub] offline translation"
        } else {
            null
        }
        val detected = request.sourceLanguage ?: SupportedLanguage.English
        return WhisperResult(
            transcript = transcript,
            translation = translation,
            detectedLanguage = detected
        )
    }
}
