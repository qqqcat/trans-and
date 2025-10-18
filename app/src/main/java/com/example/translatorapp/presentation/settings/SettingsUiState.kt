package com.example.translatorapp.presentation.settings

import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.offline.OfflineModelState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val availableLanguages: List<SupportedLanguage> = SupportedLanguage.entries,
    val availableModels: List<TranslationModelProfile> = TranslationModelProfile.entries,
    val isLoading: Boolean = true,
    val message: String? = null,
    val offlineState: OfflineModelState = OfflineModelState(),
    val isAutoDetectEnabled: Boolean = settings.direction.isAutoDetect,
    val accountEmail: String = settings.accountEmail.orEmpty(),
    val accountDisplayName: String = settings.accountDisplayName.orEmpty(),
    val syncEnabled: Boolean = settings.syncEnabled,
    val isSyncing: Boolean = false,
    val lastSyncDisplay: String? = settings.lastSyncedAt?.toReadableString(),
    val apiEndpoint: String = settings.apiEndpoint,
    val apiEndpointError: String? = null
)

private fun Instant.toReadableString(): String {
    val local = this.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%04d-%02d-%02d %02d:%02d".format(local.year, local.monthNumber, local.dayOfMonth, local.hour, local.minute)
}

