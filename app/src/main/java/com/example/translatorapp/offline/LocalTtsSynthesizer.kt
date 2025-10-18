package com.example.translatorapp.offline

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.translatorapp.domain.model.SupportedLanguage
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LocalTtsSynthesizer @Inject constructor(
    private val context: Context
) {
    private val mutex = Mutex()
    private var textToSpeech: TextToSpeech? = null

    suspend fun warmUp(preferredLanguage: SupportedLanguage) {
        ensureInitialized(preferredLanguage)
    }

    suspend fun synthesize(text: String, language: SupportedLanguage): ByteArray? {
        ensureInitialized(language)
        // Real synthesis will stream audio buffer into ByteArrayOutputStream.
        // Placeholder returns null until implementation is completed.
        Log.d("LocalTtsSynthesizer", "synthesize() called with text=$text language=${language.code}")
        return null
    }

    fun shutdown() {
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private suspend fun ensureInitialized(language: SupportedLanguage) {
        mutex.withLock {
            if (textToSpeech != null) return
            withContext(Dispatchers.Main) {
                textToSpeech = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val locale = language.toLocale()
                        textToSpeech?.language = locale
                    } else {
                        Log.w("LocalTtsSynthesizer", "Failed to initialize TextToSpeech status=$status")
                    }
                }
            }
        }
    }

    private fun SupportedLanguage.toLocale(): Locale =
        Locale.forLanguageTag(code)
}
