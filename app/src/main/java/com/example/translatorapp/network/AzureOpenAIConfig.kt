package com.example.translatorapp.network

class AzureOpenAIConfig(
    endpoint: String,
    val apiKey: String,
    val realtimeApiVersion: String,
    val textApiVersion: String,
    val transcriptionApiVersion: String
) {
    private val httpCompatibleEndpoint: String = ApiConfig.normalizeToHttp(endpoint)
    val normalizedEndpoint: String =
        if (httpCompatibleEndpoint.endsWith('/')) httpCompatibleEndpoint else "$httpCompatibleEndpoint/"
}
