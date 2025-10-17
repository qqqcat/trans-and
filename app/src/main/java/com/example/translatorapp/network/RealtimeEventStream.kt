package com.example.translatorapp.network

import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationInputMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

@Singleton
class RealtimeEventStream @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val apiConfig: ApiConfig,
    private val config: RealtimeEventStreamConfig
) {
    private val socketClient: OkHttpClient = okHttpClient.newBuilder()
        .pingInterval(config.heartbeatIntervalSeconds, TimeUnit.SECONDS)
        .build()

    fun listen(sessionId: String, token: String): Flow<TranslationContent> = callbackFlow {
        val shouldReconnect = AtomicBoolean(true)
        val url = buildUrl(sessionId, token)
        var currentDelayMs = config.initialRetryDelayMs
        var reconnectAttempts = 0
        var reconnectJob: Job? = null
        var heartbeatJob: Job? = null
        var activeSocket: WebSocket? = null

        fun cleanupSocket(socket: WebSocket) {
            if (activeSocket === socket) {
                activeSocket = null
            }
            heartbeatJob?.cancel()
            heartbeatJob = null
        }

        fun terminate(cause: Throwable?) {
            if (shouldReconnect.getAndSet(false)) {
                reconnectJob?.cancel()
                heartbeatJob?.cancel()
            }
            close(cause)
        }

        fun scheduleReconnect(lastError: Throwable?) {
            if (!shouldReconnect.get()) return
            val maxAttempts = config.maxReconnectAttempts
            if (maxAttempts > 0 && reconnectAttempts >= maxAttempts) {
                terminate(lastError ?: IllegalStateException("Exceeded max reconnect attempts"))
                return
            }
            val baseDelay = currentDelayMs
            val jitter = if (config.retryJitterMs > 0L) {
                Random.nextLong(config.retryJitterMs)
            } else {
                0L
            }
            val delayMs = min(baseDelay + jitter, config.maxRetryDelayMs)
            reconnectJob?.cancel()
            reconnectJob = launch {
                delay(delayMs)
                if (!shouldReconnect.get() || !isActive) return@launch
                reconnectAttempts += 1
                connect()
            }
            currentDelayMs = (currentDelayMs * config.retryMultiplier).toLong()
                .coerceAtMost(config.maxRetryDelayMs)
        }

        fun connect() {
            val request = Request.Builder().url(url).build()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    currentDelayMs = config.initialRetryDelayMs
                    reconnectAttempts = 0
                    heartbeatJob?.cancel()
                    config.outboundHeartbeatPayload?.let { payload ->
                        heartbeatJob = launch {
                            while (isActive && shouldReconnect.get()) {
                                delay(TimeUnit.SECONDS.toMillis(config.heartbeatIntervalSeconds))
                                if (!webSocket.send(payload)) {
                                    break
                                }
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleMessage(bytes.utf8())
                }

                private fun handleMessage(message: String) {
                    when (val action = parseEvent(message)) {
                        is RelayEventAction.Translation -> trySendBlocking(action.content)
                        RelayEventAction.KeepAlive -> Unit
                        is RelayEventAction.Terminate -> terminate(action.cause)
                        null -> Unit
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    cleanupSocket(webSocket)
                    if (!shouldReconnect.get()) {
                        terminate(null)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    cleanupSocket(webSocket)
                    if (shouldReconnect.get()) {
                        scheduleReconnect(null)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    cleanupSocket(webSocket)
                    if (shouldReconnect.get()) {
                        scheduleReconnect(t)
                    } else {
                        terminate(t)
                    }
                }
            }
            activeSocket = socketClient.newWebSocket(request, listener)
        }

        connect()

        awaitClose {
            shouldReconnect.set(false)
            reconnectJob?.cancel()
            heartbeatJob?.cancel()
            activeSocket?.close(NORMAL_CLOSURE_STATUS, null)
            activeSocket = null
        }
    }

    private fun buildUrl(sessionId: String, token: String): HttpUrl {
        val base = apiConfig.baseUrl.toHttpUrl()
        val scheme = if (base.isHttps) SECURE_WEBSOCKET_SCHEME else WEBSOCKET_SCHEME
        val normalizedPath = config.path.trimStart('/')
        return base.newBuilder()
            .scheme(scheme)
            .addPathSegments(normalizedPath)
            .addQueryParameter("sessionId", sessionId)
            .addQueryParameter("token", token)
            .build()
    }

    private fun parseEvent(payload: String): RelayEventAction? {
        return runCatching {
            val envelope = json.decodeFromString(RelayEventDto.serializer(), payload)
            when {
                envelope.type in KEEPALIVE_EVENT_TYPES -> RelayEventAction.KeepAlive
                envelope.type in TERMINAL_EVENT_TYPES -> RelayEventAction.Terminate(
                    SessionTerminatedException(envelope.type)
                )
                envelope.type in TRANSLATION_EVENT_TYPES -> {
                    val data = envelope.data ?: return null
                    val translation = json.decodeFromJsonElement(TranslationPayloadDto.serializer(), data)
                    if (translation.transcript.isNullOrBlank() && translation.translation.isNullOrBlank()) {
                        return null
                    }
                    val detectedLanguage = translation.detectedLanguage
                        ?: translation.sourceLanguage
                    val inputMode = translation.inputMode?.let {
                        runCatching { TranslationInputMode.valueOf(it) }.getOrNull()
                    } ?: TranslationInputMode.Voice
                    RelayEventAction.Translation(
                        TranslationContent(
                            transcript = translation.transcript.orEmpty(),
                            translation = translation.translation.orEmpty(),
                            synthesizedAudioPath = translation.audioUrl,
                            detectedSourceLanguage = SupportedLanguage.fromCode(detectedLanguage),
                            targetLanguage = SupportedLanguage.fromCode(translation.targetLanguage),
                            inputMode = inputMode
                        )
                    )
                }
                else -> null
            }
        }.getOrElse { error ->
            if (config.failOnDeserializationError) {
                RelayEventAction.Terminate(error)
            } else {
                null
            }
        }
    }

    companion object {
        private const val WEBSOCKET_SCHEME = "ws"
        private const val SECURE_WEBSOCKET_SCHEME = "wss"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private val TRANSLATION_EVENT_TYPES = setOf(
            "translation",
            "translation.partial",
            "translation.final",
            "transcript.final"
        )
        private val KEEPALIVE_EVENT_TYPES = setOf(
            "session.keepalive",
            "session.ping"
        )
        private val TERMINAL_EVENT_TYPES = setOf(
            "session.ended",
            "session.failed",
            "session.closed"
        )
    }
}

@Serializable
private data class RelayEventDto(
    @SerialName("type") val type: String,
    @SerialName("data") val data: JsonElement? = null
)

@Serializable
private data class TranslationPayloadDto(
    @SerialName("transcript") val transcript: String? = null,
    @SerialName("translation") val translation: String? = null,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("detectedLanguage") val detectedLanguage: String? = null,
    @SerialName("sourceLanguage") val sourceLanguage: String? = null,
    @SerialName("targetLanguage") val targetLanguage: String? = null,
    @SerialName("inputMode") val inputMode: String? = null,
)

private sealed interface RelayEventAction {
    data object KeepAlive : RelayEventAction
    data class Translation(val content: TranslationContent) : RelayEventAction
    data class Terminate(val cause: Throwable?) : RelayEventAction
}

data class RealtimeEventStreamConfig(
    val path: String = "session/events",
    val heartbeatIntervalSeconds: Long = 15L,
    val initialRetryDelayMs: Long = 1_000L,
    val maxRetryDelayMs: Long = 30_000L,
    val retryMultiplier: Double = 2.0,
    val retryJitterMs: Long = 500L,
    val maxReconnectAttempts: Int = 10,
    val outboundHeartbeatPayload: String? = null,
    val failOnDeserializationError: Boolean = false
)

private class SessionTerminatedException(eventType: String) : RuntimeException(
    "Session terminated by relay event: $eventType"
)
