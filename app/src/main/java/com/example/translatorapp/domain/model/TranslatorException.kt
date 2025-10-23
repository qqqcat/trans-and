package com.example.translatorapp.domain.model

class TranslatorException(
    val userMessage: String,
    val action: UiAction? = null,
    val level: UiMessageLevel = UiMessageLevel.Error,
    cause: Throwable? = null
) : Exception(userMessage, cause)
