package com.example.translatorapp.network

class AzureOpenAIConfig(
    val endpoint: String,
    val apiKey: String,
    val realtimeApiVersion: String,
    val textApiVersion: String,
    val transcriptionApiVersion: String
) {
    val normalizedEndpoint: String = if (endpoint.endsWith('/')) endpoint else "$endpoint/"
}