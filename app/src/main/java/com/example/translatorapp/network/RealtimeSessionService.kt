package com.example.translatorapp.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Headers
import kotlinx.serialization.Serializable

interface RealtimeSessionService {
    @Headers("Content-Type: application/json")
    @POST("openai/realtimeapi/sessions")
    suspend fun startSession(
        @Query("api-version") apiVersion: String = "2025-04-01-preview",
        @Query("deployment") deployment: String,
        @Body body: StartSessionRequest
    ): SessionResponse
}

@Serializable
data class StartSessionRequest(val model: String)

@Serializable
data class SessionResponse(
    val id: String,
    val client_secret: Secret,
    val expires_at: String? = null,
    val webrtc: WebRtcPayload? = null
)

@Serializable
data class Secret(val value: String)

@Serializable
data class WebRtcPayload(
    val sdp: String? = null,
    val ice_servers: List<IceServerDto>? = null
)

