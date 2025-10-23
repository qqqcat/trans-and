package com.example.translatorapp.presentation.home

import com.example.translatorapp.domain.model.LatencyMetrics
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.SessionInitializationStatus
import com.example.translatorapp.domain.model.UiMessage
import com.example.translatorapp.domain.model.UserSettings

data class HomeUiState(
    val isLoading: Boolean = true,
    val session: SessionUiState = SessionUiState(),
    val input: InputUiState = InputUiState(),
    val timeline: TimelineUiState = TimelineUiState(),
    val settings: UserSettings = UserSettings(),
    val messages: List<UiMessage> = emptyList()
)

data class SessionUiState(
    val status: SessionStatus = SessionStatus.Idle,
    val direction: LanguageDirection = UserSettings().direction,
    val model: TranslationModelProfile = UserSettings().translationProfile,
    val latency: LatencyMetrics = LatencyMetrics(),
    val initializationStatus: SessionInitializationStatus = SessionInitializationStatus.Idle,
    val initializationProgress: Float = 0f,
    val isMicrophoneOpen: Boolean = false,
    val activeSegment: TranslationContent? = null,
    val lastErrorMessage: String? = null,
    val requiresPermission: Boolean = false,
    val isMicActionInProgress: Boolean = false,
    val isStopInProgress: Boolean = false,
    val pendingMicState: Boolean? = null
)

enum class SessionStatus {
    Idle,
    Connecting,
    Streaming,
    Error,
    PermissionRequired
}

data class InputUiState(
    val selectedMode: TranslationInputMode = TranslationInputMode.Voice,
    val textValue: String = "",
    val isTextTranslating: Boolean = false,
    val isImageTranslating: Boolean = false,
    val isRecordAudioPermissionGranted: Boolean = false,
    val detectedLanguage: SupportedLanguage? = null
)

data class TimelineUiState(
    val entries: List<TranslationContent> = emptyList()
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}
