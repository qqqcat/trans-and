package com.example.translatorapp.offline

import com.example.translatorapp.audio.AudioSessionController
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.ManagedVoiceSession
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.localmodel.LocalSpeechRecognizer
import com.example.translatorapp.localmodel.WhisperRequest
import com.example.translatorapp.util.DispatcherProvider
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Singleton
class OfflineSessionManager @Inject constructor(
    private val audioSessionController: AudioSessionController,
    private val modelManager: OfflineModelManager,
    private val speechRecognizer: LocalSpeechRecognizer,
    private val dispatcherProvider: DispatcherProvider
) : ManagedVoiceSession {

    private val scope = CoroutineScope(dispatcherProvider.io)

    private val _state = MutableStateFlow(TranslationSessionState())
    override val state = _state.asStateFlow()

    private val _transcripts = MutableSharedFlow<TranslationContent>(replay = 0)
    override val transcriptStream = _transcripts.asSharedFlow()

    private var processingJob: Job? = null
    private var audioChannel: Channel<ByteArray>? = null
    private var currentModel: OfflineModel? = null
    private var sessionSettings: UserSettings? = null

    override suspend fun start(settings: UserSettings) {
        if (_state.value.isActive) return
        val modelProfile = pickModelProfile()
        val offlineModel = runCatching { modelManager.ensureModel(modelProfile) }
            .getOrElse { throwable ->
                _state.value = TranslationSessionState(
                    direction = settings.direction,
                    errorMessage = throwable.message ?: "Failed to prepare offline model"
                )
                return
            }
        sessionSettings = settings
        currentModel = offlineModel
        startProcessing(settings, offlineModel)
    }

    override suspend fun stop() {
        stopProcessing()
        audioSessionController.stopCapture()
        audioSessionController.releasePlayback()
        sessionSettings = null
        currentModel = null
        _state.value = TranslationSessionState()
    }

    override suspend fun sendTextPrompt(prompt: String) {
        // Reserved for future offline text prompts.
    }

    override suspend fun toggleMicrophone(): Boolean {
        val newState = !_state.value.isMicrophoneOpen
        if (newState) {
            val settings = sessionSettings ?: return false
            val model = currentModel ?: return false
            startProcessing(settings, model)
        } else {
            stopProcessing()
            audioSessionController.stopCapture()
        }
        _state.update { it.copy(isMicrophoneOpen = newState) }
        return newState
    }

    override suspend fun updateDirection(direction: LanguageDirection) {
        sessionSettings = sessionSettings?.copy(direction = direction)
        _state.update { it.copy(direction = direction) }
    }

    override suspend fun updateModel(profile: TranslationModelProfile) {
        sessionSettings = sessionSettings?.copy(translationProfile = profile)
        if (profile != TranslationModelProfile.Offline) {
            stop()
            return
        }
        val settings = sessionSettings ?: return
        val modelProfile = pickModelProfile()
        val offlineModel = runCatching { modelManager.ensureModel(modelProfile) }.getOrNull()
        if (offlineModel != null) {
            currentModel = offlineModel
        }
        if (_state.value.isActive && _state.value.isMicrophoneOpen && offlineModel != null) {
            startProcessing(settings, offlineModel)
        }
    }

    private fun pickModelProfile(): OfflineModelProfile {
        val installed = modelManager.state.value.installedProfiles
        return if (installed.contains(OfflineModelProfile.Turbo)) {
            OfflineModelProfile.Turbo
        } else {
            OfflineModelProfile.Tiny
        }
    }

    private suspend fun startProcessing(settings: UserSettings, model: OfflineModel) {
        stopProcessing()
        val channel = Channel<ByteArray>(Channel.BUFFERED)
        audioChannel = channel
        _state.value = _state.value.copy(
            isActive = true,
            isMicrophoneOpen = true,
            direction = settings.direction,
            errorMessage = null
        )
        audioSessionController.startCapture { buffer ->
            channel.trySend(buffer)
        }
        processingJob = scope.launch {
            processAudio(channel, settings, model)
        }
    }

    private suspend fun stopProcessing() {
        val channel = audioChannel
        audioChannel = null
        channel?.close()
        processingJob?.cancelAndJoin()
        processingJob = null
    }

    private suspend fun processAudio(
        channel: Channel<ByteArray>,
        settings: UserSettings,
        model: OfflineModel
    ) {
        val aggregator = ByteArrayOutputStream()
        var accumulatedSamples = 0
        try {
            for (chunk in channel) {
                aggregator.write(chunk)
                accumulatedSamples += chunk.size / BYTES_PER_SAMPLE
                if (accumulatedSamples >= MIN_SAMPLES_PER_CHUNK) {
                    val audioBytes = aggregator.toByteArray()
                    aggregator.reset()
                    accumulatedSamples = 0
                    postTranscription(audioBytes, settings, model)
                }
            }
        } catch (_: CancellationException) {
            // Expected when session stops.
        } finally {
            aggregator.reset()
        }
    }

    private suspend fun postTranscription(
        audioBytes: ByteArray,
        settings: UserSettings,
        model: OfflineModel
    ) {
        val startTime = Clock.System.now()
        val request = WhisperRequest(
            sampleRate = SAMPLE_RATE,
            sourceLanguage = settings.direction.sourceLanguage,
            targetLanguage = settings.direction.targetLanguage,
            enableTranslation = model.supportsTranslation,
            modelProfile = model.profile
        )
        val result = speechRecognizer.transcribe(audioBytes, request)
        val emitted = TranslationContent(
            transcript = result.transcript,
            translation = result.translation ?: result.transcript,
            detectedSourceLanguage = result.detectedLanguage,
            targetLanguage = settings.direction.targetLanguage,
            timestamp = Clock.System.now(),
            inputMode = TranslationInputMode.Voice
        )
        _transcripts.emit(emitted)
        _state.update { current ->
            current.copy(
                currentSegment = emitted,
                latencyMetrics = current.latencyMetrics.copy(
                    asrLatencyMs = computeLatency(startTime)
                )
            )
        }
    }

    private fun computeLatency(start: Instant): Long {
        val duration = (Clock.System.now() - start).inWholeMilliseconds
        return max(0L, duration)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MIN_WINDOW_MS = 1_500
        private const val BYTES_PER_SAMPLE = 2
        private val MIN_SAMPLES_PER_CHUNK = (SAMPLE_RATE * MIN_WINDOW_MS) / 1_000
    }
}
