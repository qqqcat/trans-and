
package com.example.translatorapp.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IceCandidateDto(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("candidate") val candidate: String,
    @SerialName("sdpMid") val sdpMid: String? = null,
    @SerialName("sdpMLineIndex") val sdpMLineIndex: Int? = null
)

@Serializable
data class SessionStartRequest(
    @SerialName("direction") val direction: String,
    @SerialName("model") val model: String
)

@Serializable
data class SessionStartResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("token") val token: String,
    @SerialName("iceServers") val iceServers: List<IceServerDto> = emptyList()
)

@Serializable
data class SessionUpdateRequest(
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("direction") val direction: String? = null,
    @SerialName("webrtcAnswer") val webrtcAnswer: String? = null
)

@Serializable
data class SessionMetricsRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("latency") val latency: Long,
    @SerialName("errorCode") val errorCode: String? = null
)

@Serializable
data class SessionStopRequest(
    @SerialName("sessionId") val sessionId: String
)
    @SerialName("webrtcSdp") val webrtcSdp: String? = null,
@Serializable
    @SerialName("clientSecret") val clientSecret: String? = null,
data class IceServerDto(
    @SerialName("urls") val urls: List<String>,
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null
)

@Serializable
data class TextTranslationRequest(
    @SerialName("text") val text: String,
    @SerialName("sourceLanguage") val sourceLanguage: String? = null,
    @SerialName("targetLanguage") val targetLanguage: String,
    @SerialName("model") val model: String
)

@Serializable
data class TextTranslationResponse(
    @SerialName("translation") val translation: String,
    @SerialName("detectedLanguage") val detectedLanguage: String? = null,
    @SerialName("targetLanguage") val targetLanguage: String? = null,
    @SerialName("sourceText") val sourceText: String? = null
)

@Serializable
data class ImageTranslationRequest(
    @SerialName("imageBase64") val imageBase64: String,
    @SerialName("sourceLanguage") val sourceLanguage: String? = null,
    @SerialName("targetLanguage") val targetLanguage: String,
    @SerialName("model") val model: String
)

@Serializable
data class LanguageDetectionRequest(
    @SerialName("text") val text: String
)

@Serializable
data class LanguageDetectionResponse(
    @SerialName("language") val language: String
)

@Serializable
data class AccountHistoryItemDto(
    @SerialName("id") val id: Long,
    @SerialName("sourceText") val sourceText: String,
    @SerialName("translatedText") val translatedText: String,
    @SerialName("direction") val direction: String,
    @SerialName("inputMode") val inputMode: String,
    @SerialName("detectedLanguage") val detectedLanguage: String? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("isFavorite") val isFavorite: Boolean = false,
    @SerialName("createdAt") val createdAt: String
)

@Serializable
data class AccountSyncRequest(
    @SerialName("accountId") val accountId: String,
    @SerialName("history") val history: List<AccountHistoryItemDto>
)

@Serializable
data class AccountSyncResponse(
    @SerialName("syncedAt") val syncedAt: String
)

@Serializable
data class AccountProfileRequest(
    @SerialName("accountId") val accountId: String?,
    @SerialName("email") val email: String,
    @SerialName("displayName") val displayName: String? = null
)

@Serializable
data class AccountProfileResponse(
    @SerialName("accountId") val accountId: String,
    @SerialName("email") val email: String,
    @SerialName("displayName") val displayName: String? = null
)
