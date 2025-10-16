package com.example.translatorapp.presentation.settings

import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.UserSettings

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val availableDirections: List<LanguageDirection> = LanguageDirection.entries,
    val availableModels: List<TranslationModelProfile> = TranslationModelProfile.entries,
    val isLoading: Boolean = true,
    val message: String? = null
)
