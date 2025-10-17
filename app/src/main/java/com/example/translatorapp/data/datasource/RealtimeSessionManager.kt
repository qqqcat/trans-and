package com.example.translatorapp.data.datasource

import com.example.translatorapp.audio.AudioSessionController
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSession
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.network.ApiConfig
import com.example.translatorapp.network.IceServerDto
import com.example.translatorapp.network.RealtimeApi
import com.example.translatorapp.network.RealtimeApiFactory
import com.example.translatorapp.network.SessionStartRequest
import com.example.translatorapp.network.ensureTrailingSlash
import com.example.translatorapp.util.DispatcherProvider
import com.example.translatorapp.webrtc.WebRtcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

@Singleton
class RealtimeSessionManager @Inject constructor(
    private val realtimeApiFactory: RealtimeApiFactory,
    private val preferencesDataSource: UserPreferencesDataSource,
    private val audioSessionController: AudioSessionController,
    private val webRtcClient: WebRtcClient,
    private val dispatcherProvider: DispatcherProvider,
    private val apiConfig: ApiConfig
) : TranslationSession {

    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(TranslationSessionState())
    override val state = _state.asStateFlow()

    private val _transcripts = MutableSharedFlow<TranslationContent>(replay = 0)
    override val transcriptStream = _transcripts.asSharedFlow()

    private var sessionId: String? = null
    private var sessionJob: Job? = null
    private var lastAudioTimestamp: Instant? = null

    @Volatile
    private var cachedApi: Pair<String, RealtimeApi>? = null

    private suspend fun realtimeApi(): RealtimeApi {
        val settings = preferencesDataSource.settings.first()
        val baseUrl = (settings.apiEndpoint.takeIf { it.isNotBlank() } ?: apiConfig.baseUrl).ensureTrailingSlash()
        val cached = cachedApi
        if (cached != null && cached.first == baseUrl) {
            return cached.second
        }
        val fresh = realtimeApiFactory.create(baseUrl)
        cachedApi = baseUrl to fresh
        return fresh
    }

    override suspend fun start(settings: UserSettings) {
        mutex.withLock {
            if (_state.value.isActive) return
            _state.value = _state.value.copy(
                isActive = true,
                direction = settings.direction,
                errorMessage = null
            )
            try {
                val api = realtimeApi()
                val response = api.startSession(
                    SessionStartRequest(
                        direction = settings.direction.encode(),
                        model = settings.translationProfile.name,
                        offlineFallback = settings.offlineFallbackEnabled
                    )
                )
                sessionId = response.sessionId
                webRtcClient.createPeerConnection(response.iceServers.toIceServers())
                webRtcClient.setRemoteDescription(
                    SessionDescription(SessionDescription.Type.OFFER, response.webrtcSdp)
                )
                val answer = webRtcClient.createAnswer()
                    ?: error("Unable to create local SDP answer")
                api.sendSdpAnswer(response.sessionId, answer.description)
                audioSessionController.startCapture { buffer ->
                    if (webRtcClient.sendAudioFrame(buffer)) {
                        lastAudioTimestamp = Clock.System.now()
                    }
                }
                _state.value = _state.value.copy(isMicrophoneOpen = true)
                sessionJob = coroutineScope.launch {
                    webRtcClient.remoteAudio.collect { audioBytes ->
                        audioSessionController.playAudio(audioBytes)
                    }
                }
            } catch (t: Throwable) {
                audioSessionController.stopCapture()
                audioSessionController.releasePlayback()
                webRtcClient.close()
                sessionJob?.cancel()
                sessionJob = null
                sessionId = null
                lastAudioTimestamp = null
                _state.value = TranslationSessionState(
                    direction = settings.direction,
                    errorMessage = t.message ?: "Failed to establish realtime session"
                )
            }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            sessionJob?.cancel()
            sessionJob = null
            audioSessionController.stopCapture()
            audioSessionController.releasePlayback()
            webRtcClient.close()
            val api = realtimeApi()
            sessionId?.let { runCatching { api.stopSession(it) } }
            sessionId = null
            lastAudioTimestamp = null
            _state.value = TranslationSessionState()
        }
    }

    override suspend fun sendTextPrompt(prompt: String) {
        // Reserved for non-voice prompts.
    }

    suspend fun toggleMicrophone(): Boolean = mutex.withLock {
        val newState = !_state.value.isMicrophoneOpen
        if (newState) {
            if (sessionId == null) {
                return false
            }
            audioSessionController.startCapture { buffer ->
                if (webRtcClient.sendAudioFrame(buffer)) {
                    lastAudioTimestamp = Clock.System.now()
                }
            }
        } else {
            audioSessionController.stopCapture()
        }
        _state.value = _state.value.copy(isMicrophoneOpen = newState)
        newState
    }

    suspend fun updateDirection(direction: LanguageDirection) {
        mutex.withLock {
            _state.value = _state.value.copy(direction = direction)
            sessionId?.let { id ->
                runCatching {
                    realtimeApi().updateSession(id, direction = direction.encode())
                }
            }
        }
    }

    suspend fun updateModel(profile: TranslationModelProfile) {
        sessionId?.let { id ->
            runCatching {
                realtimeApi().updateSession(id, model = profile.name)
            }
        }
    }

    suspend fun onTranslationReceived(content: TranslationContent) {
        val normalized = content.copy(
            targetLanguage = content.targetLanguage ?: _state.value.direction.targetLanguage,
            detectedSourceLanguage = content.detectedSourceLanguage ?: _state.value.direction.sourceLanguage
        )
        _transcripts.emit(normalized)
        _state.value = _state.value.copy(currentSegment = normalized)
        sessionId?.let { id ->
            lastAudioTimestamp?.let { start ->
                val latency = (Clock.System.now() - start).inWholeMilliseconds
                runCatching {
                    realtimeApi().sendMetrics(id, latency)
                }
                _state.value = _state.value.copy(
                    latencyMetrics = _state.value.latencyMetrics.copy(translationLatencyMs = latency)
                )
            }
        }
    }
}

private fun List<IceServerDto>.toIceServers(): List<PeerConnection.IceServer> {
    val negotiated = mapNotNull { dto ->
        val builder = when {
            dto.urls.isEmpty() -> return@mapNotNull null
            dto.urls.size == 1 -> PeerConnection.IceServer.builder(dto.urls.first())
            else -> PeerConnection.IceServer.builder(dto.urls)
        }
        dto.username?.takeIf { it.isNotBlank() }?.let { builder.setUsername(it) }
        dto.credential?.takeIf { it.isNotBlank() }?.let { builder.setPassword(it) }
        builder.createIceServer()
    }
    return if (negotiated.isEmpty()) {
        listOf(PeerConnection.IceServer.builder(DEFAULT_STUN_SERVER).createIceServer())
    } else {
        negotiated
    }
}

private const val DEFAULT_STUN_SERVER = "stun:stun.l.google.com:19302"
