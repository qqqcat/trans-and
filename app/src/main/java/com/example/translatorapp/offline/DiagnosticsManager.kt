package com.example.translatorapp.offline

import com.example.translatorapp.audio.AudioSessionController
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.util.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Singleton
class DiagnosticsManager @Inject constructor(
    private val audioSessionController: AudioSessionController,
    private val ttsSynthesizer: LocalTtsSynthesizer,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun runMicrophoneTest(durationMillis: Long = 1_000): Float = withContext(dispatcherProvider.io) {
        if (audioSessionController.isRecordingNow()) {
            throw IllegalStateException("麦克风正被实时会话占用，请先结束会话再测试。")
        }
        val amplitudes = mutableListOf<Float>()
        audioSessionController.startCapture { buffer ->
            val rms = buffer.computeRms()
            amplitudes += rms
        }
        delay(durationMillis)
        audioSessionController.stopCapture()
        return@withContext amplitudes.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
    }

    suspend fun runTtsTest(language: SupportedLanguage): Boolean = withContext(dispatcherProvider.io) {
        ttsSynthesizer.warmUp(language)
        val result = ttsSynthesizer.synthesize("语音合成测试文本。", language)
        return@withContext result != null && result.pcm.isNotEmpty()
    }
}

private fun ByteArray.computeRms(): Float {
    if (isEmpty()) return 0f
    var sum = 0.0
    var samples = 0
    var i = 0
    while (i + 1 < size) {
        val sample = ((this[i + 1].toInt() shl 8) or (this[i].toInt() and 0xFF)).toShort()
        sum += sample * sample.toDouble()
        samples++
        i += 2
    }
    if (samples == 0) return 0f
    val mean = sum / samples
    val rms = sqrt(mean)
    return (rms / Short.MAX_VALUE).toFloat()
}
