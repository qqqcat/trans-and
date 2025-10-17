package com.example.translatorapp.network

internal fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "${this}/"
