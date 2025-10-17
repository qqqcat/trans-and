package com.example.translatorapp.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Singleton
class RealtimeApiFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    fun create(baseUrl: String): RealtimeApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return RealtimeApi(retrofit.create(ApiRelayService::class.java))
    }
}
