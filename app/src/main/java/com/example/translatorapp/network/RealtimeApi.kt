package com.example.translatorapp.network

import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class RealtimeApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val config: AzureOpenAIConfig
) {

    private val sessionDeployments = ConcurrentHashMap<String, String>()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun startSession(request: SessionStartRequest): SessionStartResponse {
        val deployment = request.model
        val direction = LanguageDirection.decode(request.direction)
        val instructions = buildInstructions(direction)
        val payload = buildJsonObject {
            put("model", JsonPrimitive(deployment))
            put("session", buildJsonObject {
                put("instructions", JsonPrimitive(instructions))
                put("modalities", buildJsonArray {
                    add(JsonPrimitive("text"))
                    add(JsonPrimitive("audio"))
                })
                put("voice", JsonPrimitive("alloy"))
                put("input_audio_format", JsonPrimitive("pcm16"))
                put("output_audio_format", JsonPrimitive("pcm16"))
            })
        }

        val url = buildRealtimeUrl(pathSegments = listOf("openai", "realtime"), deployment = deployment)
        val requestBuilder = Request.Builder()
            .url(url)
            .applyAzureHeaders()
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMediaType))

        val responseBody = executeRequest(requestBuilder)
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val sessionId = parsed["id"]?.jsonPrimitive?.content
            ?: error("Missing session id in realtime response")
        val clientSecret = parsed["client_secret"]?.jsonObject?.get("value")?.jsonPrimitive?.content
            ?: error("Missing client secret in realtime response")
        val webrtc = parsed["webrtc"]?.jsonObject
            ?: error("Missing WebRTC negotiation payload")
        val sdp = webrtc["sdp"]?.jsonPrimitive?.content
            ?: error("Missing WebRTC offer")
        val iceServers = webrtc["ice_servers"]?.jsonArray?.map { parseIceServer(it) }.orEmpty()
        sessionDeployments[sessionId] = deployment
        return SessionStartResponse(
            sessionId = sessionId,
            webrtcSdp = sdp,
            token = clientSecret,
            iceServers = iceServers
        )
    }

    suspend fun sendSdpAnswer(sessionId: String, sdp: String) {
        val deployment = sessionDeployments[sessionId]
            ?: error("Unknown realtime session: $sessionId")
        val url = buildRealtimeUrl(
            pathSegments = listOf("openai", "realtime", "sessions", sessionId),
            deployment = deployment
        )
        val payload = buildJsonObject {
            put("webrtc", buildJsonObject {
                put("sdp", JsonPrimitive(sdp))
                put("type", JsonPrimitive("answer"))
            })
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .applyAzureHeaders()
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMediaType))
        executeRequest(requestBuilder)
    }

    suspend fun updateSession(
        sessionId: String,
        direction: String? = null,
        model: String? = null
    ) {
        val deployment = sessionDeployments[sessionId]
            ?: return
        val payload = buildJsonObject {
            val sessionUpdate = buildJsonObject {
                direction?.let {
                    val decoded = LanguageDirection.decode(it)
                    put("instructions", JsonPrimitive(buildInstructions(decoded)))
                }
            }
            if (sessionUpdate.isNotEmpty()) {
                put("session", sessionUpdate)
            }
        }
        if (payload.isEmpty() && model == null) {
            return
        }
        model?.let { sessionDeployments[sessionId] = it }
        val url = buildRealtimeUrl(
            pathSegments = listOf("openai", "realtime", "sessions", sessionId),
            deployment = model ?: deployment
        )
        val body = if (payload.isEmpty()) {
            json.encodeToString(JsonObject.serializer(), buildJsonObject {}).toRequestBody(jsonMediaType)
        } else {
            json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMediaType)
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .applyAzureHeaders()
            .post(body)
        executeRequest(requestBuilder)
    }

    suspend fun stopSession(sessionId: String) {
        val deployment = sessionDeployments.remove(sessionId) ?: return
        val url = buildRealtimeUrl(
            pathSegments = listOf("openai", "realtime", "sessions", sessionId),
            deployment = deployment
        )
        val requestBuilder = Request.Builder()
            .url(url)
            .applyAzureHeaders()
            .delete()
        executeRequest(requestBuilder)
    }

    fun forgetSession(sessionId: String) {
        sessionDeployments.remove(sessionId)
    }

    suspend fun sendMetrics(sessionId: String, latency: Long, errorCode: String? = null) {
        // Azure Realtime API does not expose a metrics ingestion endpoint. No-op.
    }

    suspend fun translateText(request: TextTranslationRequest): TextTranslationResponse {
        val targetLanguage = SupportedLanguage.fromCode(request.targetLanguage) ?: SupportedLanguage.English
        val direction = LanguageDirection(
            sourceLanguage = request.sourceLanguage?.let { SupportedLanguage.fromCode(it) },
            targetLanguage = targetLanguage
        )
        val systemPrompt = buildTextTranslationPrompt(direction)
        val payload = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(systemPrompt))
                        })
                    })
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(request.text))
                        })
                    })
                })
            })
            put("temperature", JsonPrimitive(0.2))
        }
        val url = buildTextUrl(request.model)
        val requestBuilder = Request.Builder()
            .url(url)
            .applyAzureHeaders()
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMediaType))
        val responseBody = executeRequest(requestBuilder)
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val translation = parsed["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?: error("Translation response missing text")
        return TextTranslationResponse(
            translation = translation,
            detectedLanguage = direction.sourceLanguage?.code,
            targetLanguage = direction.targetLanguage.code,
            sourceText = request.text
        )
    }

    suspend fun translateImage(request: ImageTranslationRequest): TextTranslationResponse {
        val targetLanguage = SupportedLanguage.fromCode(request.targetLanguage) ?: SupportedLanguage.English
        val sourceLanguage = request.sourceLanguage?.let { SupportedLanguage.fromCode(it) }
        val prompt = buildString {
            append("Extract any readable text from the provided image")
            if (sourceLanguage != null) {
                append(" written in ${sourceLanguage.displayName}")
            }
            append(" and translate it into ${targetLanguage.displayName}. Respond with translation only.")
        }
        val payload = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive("You are an OCR and translation assistant."))
                        })
                    })
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(prompt))
                        })
                        add(buildJsonObject {
                            put("type", JsonPrimitive("input_image"))
                            put("image_base64", JsonPrimitive(request.imageBase64))
                        })
                    })
                })
            })
            put("temperature", JsonPrimitive(0.2))
        }
        val url = buildTextUrl(request.model)
        val requestBuilder = Request.Builder()
            .url(url)
            .applyAzureHeaders()
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMediaType))
        val responseBody = executeRequest(requestBuilder)
        val translation = json.parseToJsonElement(responseBody).jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?: error("Image translation response missing text")
        return TextTranslationResponse(
            translation = translation,
            detectedLanguage = sourceLanguage?.code,
            targetLanguage = targetLanguage.code,
            sourceText = null
        )
    }

    suspend fun detectLanguage(text: String): LanguageDetectionResponse {
        val prompt = "Detect the primary language of the following text and respond with the BCP-47 language code only: \n$text"
        val payload = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive("You are a language detection service. Respond with language codes only."))
                        })
                    })
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(prompt))
                        })
                    })
                })
            })
            put("temperature", JsonPrimitive(0.0))
            put("max_tokens", JsonPrimitive(8))
        }
        val url = buildTextUrl("gpt-4o")
        val requestBuilder = Request.Builder()
            .url(url)
            .applyAzureHeaders()
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMediaType))
        val responseBody = executeRequest(requestBuilder)
        val languageCode = json.parseToJsonElement(responseBody).jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.replace('_', '-')
            ?: "auto"
        val normalized = if (languageCode.equals("auto", ignoreCase = true)) {
            "auto"
        } else {
            SupportedLanguage.entries.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }?.code
                ?: languageCode.uppercase()
        }
        return LanguageDetectionResponse(language = normalized)
    }

    suspend fun syncAccount(request: AccountSyncRequest): AccountSyncResponse {
        // Azure OpenAI does not manage account history. Simulate an immediate sync timestamp.
        return AccountSyncResponse(syncedAt = Clock.System.now().toString())
    }

    suspend fun updateAccountProfile(request: AccountProfileRequest): AccountProfileResponse {
        return AccountProfileResponse(
            accountId = request.accountId ?: "local",
            email = request.email,
            displayName = request.displayName
        )
    }

    private fun buildRealtimeUrl(pathSegments: List<String>, deployment: String): HttpUrl {
        val base = config.normalizedEndpoint.toHttpUrl().newBuilder()
        pathSegments.forEach { base.addPathSegment(it) }
        base.addQueryParameter("api-version", config.realtimeApiVersion)
        base.addQueryParameter("deployment", deployment)
        return base.build()
    }

    private fun buildTextUrl(deployment: String): HttpUrl {
        val base = config.normalizedEndpoint.toHttpUrl().newBuilder()
        base.addPathSegment("openai")
        base.addPathSegment("deployments")
        base.addPathSegment(deployment)
        base.addPathSegment("chat")
        base.addPathSegment("completions")
        base.addQueryParameter("api-version", config.textApiVersion)
        return base.build()
    }

    private fun Request.Builder.applyAzureHeaders(): Request.Builder {
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("Azure OpenAI API key is missing. Set TRANSAND_AZURE_OPENAI_API_KEY before building the app.")
        }
        return header("api-key", config.apiKey)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
    }

    private fun executeRequest(builder: Request.Builder): String {
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val message = response.body?.string()
                throw IOException("Azure OpenAI request failed: ${response.code} ${response.message}. Body: $message")
            }
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private fun parseIceServer(element: JsonElement): IceServerDto {
        val obj = element.jsonObject
        val urlsElement = obj["urls"]
        val urls = when {
            urlsElement == null -> emptyList()
            urlsElement is JsonArray -> urlsElement.mapNotNull { it.jsonPrimitive.contentOrNull }
            urlsElement is JsonPrimitive && urlsElement.isString -> listOfNotNull(urlsElement.content)
            urlsElement is JsonPrimitive -> emptyList()
            else -> emptyList()
        }
        val username = obj["username"]?.jsonPrimitive?.contentOrNull
        val credential = obj["credential"]?.jsonPrimitive?.contentOrNull
        return IceServerDto(urls = urls, username = username, credential = credential)
    }

    private fun buildInstructions(direction: LanguageDirection): String {
        val sourceName = direction.sourceLanguage?.displayName ?: "讲话者的语言"
        val targetName = direction.targetLanguage.displayName
        return "You are a realtime interpreter. Listen continuously in $sourceName and deliver fluent, natural translations into $targetName. Keep translations concise and faithful to tone."
    }

    private fun buildTextTranslationPrompt(direction: LanguageDirection): String {
        val source = direction.sourceLanguage?.displayName ?: "源语言"
        val target = direction.targetLanguage.displayName
        return "Translate the user's input from $source into $target. Respond with translation only."
    }
}
