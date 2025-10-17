package com.example.translatorapp.presentation.home

import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings

data class HomeUiState(
    val isLoading: Boolean = true,
    val sessionState: TranslationSessionState = TranslationSessionState(),
    val transcriptHistory: List<TranslationContent> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val isMicActive: Boolean = false,
    val errorMessage: String? = null,
    val isRecordAudioPermissionGranted: Boolean = false,
)
