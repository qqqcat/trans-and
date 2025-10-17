package com.example.translatorapp.network

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiRelayService {
    @POST("session/start")
    suspend fun startSession(@Body body: SessionStartRequest): SessionStartResponse

    @POST("session/update")
    suspend fun updateSession(@Body body: SessionUpdateRequest)

    @POST("session/stop")
    suspend fun stopSession(@Body body: SessionStopRequest)

    @POST("session/metrics")
    suspend fun sendMetrics(@Body body: SessionMetricsRequest)

    @POST("text/translate")
    suspend fun translateText(@Body body: TextTranslationRequest): TextTranslationResponse

    @POST("image/translate")
    suspend fun translateImage(@Body body: ImageTranslationRequest): TextTranslationResponse

    @POST("language/detect")
    suspend fun detectLanguage(@Body body: LanguageDetectionRequest): LanguageDetectionResponse

    @POST("account/sync")
    suspend fun syncAccount(@Body body: AccountSyncRequest): AccountSyncResponse

    @POST("account/profile")
    suspend fun updateAccountProfile(@Body body: AccountProfileRequest): AccountProfileResponse
}
