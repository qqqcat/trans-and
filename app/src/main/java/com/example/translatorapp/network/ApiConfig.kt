package com.example.translatorapp.network

data class ApiConfig(private val rawBaseUrl: String) {
    val baseUrl: String = normalizeToHttp(rawBaseUrl)

    companion object {
        fun normalizeToHttp(value: String): String {
            val trimmed = value.trim().removeSuffix("/")
            val httpCompatible = when {
                trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substringAfter("://")
                trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substringAfter("://")
                else -> trimmed
            }
            return if (httpCompatible.endsWith("/")) httpCompatible else "$httpCompatible/"
        }
    }
}
