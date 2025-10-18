package com.example.translatorapp.offline

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.translatorapp.domain.model.SupportedLanguage
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TtsSynthesisResult(
    val pcm: ByteArray,
    val cachePath: String?,
    val sampleRate: Int
)

@Singleton
class LocalTtsSynthesizer @Inject constructor(
    private val context: Context
) {
    private val mutex = Mutex()
    private var textToSpeech: TextToSpeech? = null
    private val pending = ConcurrentHashMap<String, PendingSynthesis>()
    private val cacheDir: File = File(context.filesDir, "tts-cache").apply { mkdirs() }

    private data class PendingSynthesis(
        val file: File,
        val deferred: CompletableDeferred<File>
    )

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            // no-op
        }

        override fun onDone(utteranceId: String) {
            val entry = pending.remove(utteranceId) ?: return
            entry.deferred.complete(entry.file)
        }

        override fun onError(utteranceId: String) {
            val entry = pending.remove(utteranceId) ?: return
            entry.deferred.completeExceptionally(IOException("TTS error for $utteranceId"))
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            val entry = pending.remove(utteranceId) ?: return
            entry.deferred.completeExceptionally(IOException("TTS error $errorCode for $utteranceId"))
        }
    }

    suspend fun warmUp(preferredLanguage: SupportedLanguage) {
        ensureInitialized(preferredLanguage)
    }

    suspend fun synthesize(text: String, language: SupportedLanguage): TtsSynthesisResult? {
        if (text.isBlank()) return null
        ensureInitialized(language)
        val tts = textToSpeech ?: return null
        val locale = language.toLocale()
        withContext(Dispatchers.Main) {
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("LocalTtsSynthesizer", "Language ${language.code} unsupported by system TTS")
            }
        }

        val cacheFile = cacheFile(language, text)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            val parsed = extractPcm(cacheFile)
            return TtsSynthesisResult(parsed.pcm, cacheFile.absolutePath, parsed.sampleRate)
        }

        val utteranceId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<File>()
        pending[utteranceId] = PendingSynthesis(cacheFile, deferred)

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, TextToSpeech.Engine.DEFAULT_STREAM)
        }

        withContext(Dispatchers.Main) {
            val result = tts.synthesizeToFile(text, params, cacheFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                pending.remove(utteranceId)
                deferred.completeExceptionally(IOException("Failed to enqueue synthesis (code=$result)"))
            }
        }

        return try {
            deferred.await()
            val parsed = extractPcm(cacheFile)
            TtsSynthesisResult(parsed.pcm, cacheFile.absolutePath, parsed.sampleRate)
        } catch (error: Throwable) {
            cacheFile.delete()
            Log.w("LocalTtsSynthesizer", "Failed to synthesize speech", error)
            null
        } finally {
            pending.remove(utteranceId)
        }
    }

    fun shutdown() {
        textToSpeech?.shutdown()
        textToSpeech = null
        pending.values.forEach { it.deferred.cancel() }
        pending.clear()
    }

    private suspend fun ensureInitialized(preferredLanguage: SupportedLanguage) {
        if (textToSpeech != null) return
        mutex.withLock {
            if (textToSpeech != null) return
            withContext(Dispatchers.Main) {
                textToSpeech = TextToSpeech(context) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        Log.w("LocalTtsSynthesizer", "Failed to initialize TextToSpeech status=$status")
                    }
                }.apply {
                    setOnUtteranceProgressListener(progressListener)
                    setLanguage(preferredLanguage.toLocale())
                }
            }
        }
    }

    private fun cacheFile(language: SupportedLanguage, text: String): File {
        val key = hash("${language.code}|$text")
        return File(cacheDir, "$key.wav")
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class ParsedWav(val pcm: ByteArray, val sampleRate: Int)

    private fun extractPcm(file: File): ParsedWav {
        val bytes = file.readBytes()
        var offset = 12
        var sampleRate = 16_000
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "fmt ") {
                sampleRate = ByteBuffer.wrap(bytes, offset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
            }
            if (chunkId == "data") {
                val dataStart = offset + 8
                val end = (dataStart + chunkSize).coerceAtMost(bytes.size)
                val pcm = bytes.copyOfRange(dataStart, end)
                return ParsedWav(pcm = pcm, sampleRate = sampleRate)
            }
            offset += 8 + chunkSize
        }
        throw IOException("Invalid WAV file: ${file.absolutePath}")
    }

    private fun SupportedLanguage.toLocale(): Locale =
        Locale.forLanguageTag(code)
}
