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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val azureConfig: AzureOpenAIConfig,
    private val config: RealtimeEventStreamConfig
) {
    private val socketClient: OkHttpClient = okHttpClient.newBuilder()
        .pingInterval(config.heartbeatIntervalSeconds, TimeUnit.SECONDS)
        .build()

    fun listen(sessionId: String, token: String, deployment: String): Flow<TranslationContent> = callbackFlow {
        val shouldReconnect = AtomicBoolean(true)
        // 使用新版 WebSocket URL 生成逻辑，确保与 Azure 官方文档一致
        val wsUrl = RealtimeApi(okHttpClient, json, azureConfig).buildRealtimeWebSocketUrl(deployment)
        val url = wsUrl.toHttpUrl()
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

        fun connect() {
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

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
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

    private fun buildUrl(sessionId: String, deployment: String): HttpUrl {
        val base = azureConfig.normalizedEndpoint.toHttpUrl()
        val scheme = if (base.isHttps) SECURE_WEBSOCKET_SCHEME else WEBSOCKET_SCHEME
        val normalizedPath = config.path.trim('/')
        val builder = base.newBuilder().scheme(scheme)
        if (normalizedPath.isNotEmpty()) {
            builder.addPathSegments(normalizedPath)
        }
        config.queryParameters.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        builder.addQueryParameter("deployment", deployment)
        builder.addQueryParameter("session", sessionId)
        return builder.build()
    }

    private fun parseEvent(payload: String): RelayEventAction? {
        val element = runCatching { json.decodeFromString<JsonElement>(payload) }.getOrElse { error ->
            return if (config.failOnDeserializationError) RelayEventAction.Terminate(error) else null
        }
        if (element !is JsonObject) {
            return null
        }
        val type = element["type"]?.jsonPrimitive?.content ?: return null
        if (type in KEEPALIVE_EVENT_TYPES) {
            return RelayEventAction.KeepAlive
        }
        if (type in TERMINAL_EVENT_TYPES) {
            return RelayEventAction.Terminate(SessionTerminatedException(type))
        }
        if (type.startsWith("response.")) {
            val delta = element["delta"]?.jsonPrimitive?.contentOrNull
            if (delta.isNullOrBlank()) {
                return null
            }
            val isOutput = type.contains("output_text")
            val transcript = if (isOutput) "" else delta
            val translation = if (isOutput) delta else ""
            return RelayEventAction.Translation(
                TranslationContent(
                    transcript = transcript,
                    translation = translation,
                    inputMode = TranslationInputMode.Voice
                )
            )
        }
        val data = element["data"]
        if (data != null && data is JsonObject) {
            val translation = runCatching {
                json.decodeFromJsonElement(TranslationPayloadDto.serializer(), data)
            }.getOrNull()
            if (translation != null) {
                if (translation.transcript.isNullOrBlank() && translation.translation.isNullOrBlank()) {
                    return null
                }
                val detectedLanguage = translation.detectedLanguage ?: translation.sourceLanguage
                val inputMode = translation.inputMode?.let {
                    runCatching { TranslationInputMode.valueOf(it) }.getOrNull()
                } ?: TranslationInputMode.Voice
                return RelayEventAction.Translation(
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
        }
        return null
    }

    companion object {
        private const val WEBSOCKET_SCHEME = "ws"
        private const val SECURE_WEBSOCKET_SCHEME = "wss"
        private const val NORMAL_CLOSURE_STATUS = 1000
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
    val queryParameters: Map<String, String> = emptyMap(),
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
