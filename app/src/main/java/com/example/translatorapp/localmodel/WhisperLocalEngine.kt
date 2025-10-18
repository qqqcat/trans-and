package com.example.translatorapp.localmodel

import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.offline.OfflineModelProfile
import com.example.translatorapp.util.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WhisperRequest(
    val sampleRate: Int,
    val sourceLanguage: SupportedLanguage?,
    val targetLanguage: SupportedLanguage,
    val enableTranslation: Boolean,
    val modelProfile: OfflineModelProfile,
    val modelPath: String,
    val threadCount: Int
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
class WhisperLocalEngine @Inject constructor(
    private val dispatcherProvider: DispatcherProvider
) : LocalSpeechRecognizer {

    private val handleMutex = Mutex()
    private val handles = mutableMapOf<String, Long>()
    private val bridge = WhisperNativeBridge()

    override suspend fun transcribe(audio: ByteArray, request: WhisperRequest): WhisperResult =
        withContext(dispatcherProvider.default) {
            val handle = ensureHandle(request.modelPath, request.threadCount)
            val json = bridge.process(
                handle = handle,
                audio = audio,
                sampleRate = request.sampleRate,
                sourceLanguage = request.sourceLanguage?.code,
                targetLanguage = request.targetLanguage.code,
                enableTranslation = request.enableTranslation,
                preferredThreads = request.threadCount
            )
            parseResult(json, request)
        }

    private suspend fun ensureHandle(modelPath: String, threads: Int): Long =
        handleMutex.withLock {
            handles[modelPath] ?: run {
                val handle = bridge.init(modelPath, threads)
                require(handle != 0L) { "Failed to initialise whisper context for $modelPath" }
                handles[modelPath] = handle
                handle
            }
        }

    private fun parseResult(payload: String, request: WhisperRequest): WhisperResult {
        val json = JSONObject(payload)
        if (json.has("error")) {
            throw IllegalStateException("Whisper native error: ${json.getString("error")}")
        }
        val transcript = json.optString("transcript")
        val translationRaw = json.optString("translation")
        val detectedCode = json.optString("language")
        val detected = detectedCode.takeIf { it.isNotBlank() }?.let(SupportedLanguage::fromCode)
        val translation = translationRaw.takeIf { it.isNotBlank() }
            ?: if (request.enableTranslation) transcript else null
        return WhisperResult(
            transcript = transcript,
            translation = translation,
            detectedLanguage = detected
        )
    }

    suspend fun clear() {
        val handlesToRelease = handleMutex.withLock {
            val snapshot = handles.toMap()
            handles.clear()
            snapshot
        }
        for ((_, handle) in handlesToRelease) {
            bridge.release(handle)
        }
    }
}
