package com.example.translatorapp.presentation.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.ThemeMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.SyncAccountUseCase
import com.example.translatorapp.domain.usecase.UpdateAccountProfileUseCase
import com.example.translatorapp.domain.usecase.UpdateApiEndpointUseCase
import com.example.translatorapp.domain.usecase.UpdateDirectionUseCase
import com.example.translatorapp.domain.usecase.UpdateModelUseCase
import com.example.translatorapp.domain.usecase.UpdateSyncEnabledUseCase
import com.example.translatorapp.domain.usecase.UpdateThemeModeUseCase
import com.example.translatorapp.domain.usecase.UpdateTelemetryConsentUseCase
import com.example.translatorapp.domain.usecase.UpdateAppLanguageUseCase
import com.example.translatorapp.util.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val updateDirectionUseCase: UpdateDirectionUseCase,
    private val updateModelUseCase: UpdateModelUseCase,
    private val updateTelemetryConsentUseCase: UpdateTelemetryConsentUseCase,
    private val updateAccountProfileUseCase: UpdateAccountProfileUseCase,
    private val updateSyncEnabledUseCase: UpdateSyncEnabledUseCase,
    private val syncAccountUseCase: SyncAccountUseCase,
    private val updateThemeModeUseCase: UpdateThemeModeUseCase,
    private val updateAppLanguageUseCase: UpdateAppLanguageUseCase,
    private val updateApiEndpointUseCase: UpdateApiEndpointUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var manualSourceLanguage: SupportedLanguage = SupportedLanguage.ChineseSimplified

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            refreshSettings(preserveInputs = false)
        }
    }

    fun onDirectionSelected(direction: LanguageDirection) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateDirectionUseCase(direction)
            refreshSettings(message = application.getString(R.string.settings_message_language_direction_updated))
        }
    }

    fun onSourceLanguageSelected(language: SupportedLanguage) {
        manualSourceLanguage = language
        viewModelScope.launch(dispatcherProvider.io) {
            val current = loadSettingsUseCase()
            if (!current.direction.isAutoDetect) {
                updateDirectionUseCase(current.direction.withSource(language))
                refreshSettings(message = application.getString(R.string.settings_message_source_language_updated))
            } else {
                refreshSettings()
            }
        }
    }

    fun onTargetLanguageSelected(language: SupportedLanguage) {
        viewModelScope.launch(dispatcherProvider.io) {
            val current = loadSettingsUseCase()
            val updatedDirection = current.direction.withTarget(language)
            updateDirectionUseCase(updatedDirection)
            refreshSettings(message = application.getString(R.string.settings_message_target_language_updated))
        }
    }

    fun onAutoDetectChanged(enabled: Boolean) {

        viewModelScope.launch(dispatcherProvider.io) {

            val current = loadSettingsUseCase()

            val direction = if (enabled) {

                current.direction.withSource(null)

            } else {

                current.direction.withSource(manualSourceLanguage)

            }

            updateDirectionUseCase(direction)

            refreshSettings(message = if (enabled) application.getString(R.string.settings_message_auto_detect_enabled) else application.getString(R.string.settings_message_auto_detect_disabled))

        }

    }



    fun onModelSelected(profile: TranslationModelProfile) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateModelUseCase(profile)
            refreshSettings(message = application.getString(R.string.settings_message_model_switched, profile.displayName))
        }
    }

    fun onTelemetryChanged(consent: Boolean) {

        viewModelScope.launch(dispatcherProvider.io) {

            updateTelemetryConsentUseCase(consent)

            refreshSettings(message = if (consent) application.getString(R.string.settings_message_data_sharing_enabled) else application.getString(R.string.settings_message_data_sharing_disabled))

        }

    }



    fun onThemeModeSelected(themeMode: ThemeMode) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateThemeModeUseCase(themeMode)
            val message = when (themeMode) {
                ThemeMode.System -> "Theme now follows system setting"
                ThemeMode.Light -> "Light theme enabled"
                ThemeMode.Dark -> "Dark theme enabled"
            }
            refreshSettings(message = message)
        }
    }

    fun onAppLanguageSelected(language: String): Job {
        _uiState.update { it.copy(selectedAppLanguage = language) }
        return viewModelScope.launch(dispatcherProvider.io) {
            updateAppLanguageUseCase(language)
            refreshSettings(message = null)
        }
    }

    fun onAccountEmailChange(value: String) {
        _uiState.update { it.copy(accountEmail = value) }
    }

    fun onAccountDisplayNameChange(value: String) {
        _uiState.update { it.copy(accountDisplayName = value) }
    }

    fun onApiEndpointChange(value: String) {
        _uiState.update { it.copy(apiEndpoint = value, apiEndpointError = null, message = null) }
    }

    fun onSaveApiEndpoint() {
        val raw = uiState.value.apiEndpoint.trim()
        val error = validateEndpoint(raw)
        if (error != null) {
            _uiState.update { it.copy(apiEndpointError = error) }
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            updateApiEndpointUseCase(normalizeEndpoint(raw))
            refreshSettings(message = if (raw.isBlank()) application.getString(R.string.settings_message_service_address_restored) else application.getString(R.string.settings_message_service_address_updated))
        }
    }

    fun onResetApiEndpoint() {
        viewModelScope.launch(dispatcherProvider.io) {
            updateApiEndpointUseCase("")
            refreshSettings(message = application.getString(R.string.settings_message_service_address_restored))
        }
    }

    fun onSaveAccount() {
        val email = uiState.value.accountEmail.trim()
        if (email.isEmpty()) {
            _uiState.update { it.copy(message = application.getString(R.string.settings_message_invalid_email)) }
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = loadSettingsUseCase()
            val accountId = settings.accountId ?: java.util.UUID.randomUUID().toString()
            updateAccountProfileUseCase(
                AccountProfile(
                    accountId = accountId,
                    email = email,
                    displayName = uiState.value.accountDisplayName.ifBlank { null }
                )
            )
            refreshSettings(message = application.getString(R.string.settings_message_account_updated), preserveInputs = false)
        }
    }

    fun onSyncToggle(enabled: Boolean) {

        viewModelScope.launch(dispatcherProvider.io) {

            updateSyncEnabledUseCase(enabled)

            refreshSettings(message = if (enabled) application.getString(R.string.settings_message_sync_enabled) else application.getString(R.string.settings_message_sync_disabled))

        }

    }



    fun onSyncNow() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isSyncing = true, message = null) }
            val result = syncAccountUseCase()
            val message = result.message ?: if (result.success) application.getString(R.string.settings_message_sync_success) else application.getString(R.string.settings_message_sync_failed)
            refreshSettings(message = message)
        }
    }

    fun onRunMicrophoneTest() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isDiagnosticsRunning = true, message = null) }
            // TODO: Implement microphone test
            kotlinx.coroutines.delay(2000) // Simulate test duration
            _uiState.update { it.copy(isDiagnosticsRunning = false, message = application.getString(R.string.settings_message_microphone_test_completed)) }
        }
    }

    fun onRunTtsTest() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isDiagnosticsRunning = true, message = null) }
            // TODO: Implement TTS test
            kotlinx.coroutines.delay(2000) // Simulate test duration
            _uiState.update { it.copy(isDiagnosticsRunning = false, message = application.getString(R.string.settings_message_tts_test_completed)) }
        }
    }

    private suspend fun refreshSettings(message: String? = null, preserveInputs: Boolean = true) {
        val settings = loadSettingsUseCase()
        manualSourceLanguage = settings.direction.sourceLanguage ?: manualSourceLanguage
        _uiState.update { current ->
            val email = if (preserveInputs && current.accountEmail.isNotBlank() && current.accountEmail != settings.accountEmail.orEmpty()) {
                current.accountEmail
            } else {
                settings.accountEmail.orEmpty()
            }
            val displayName = if (preserveInputs && current.accountDisplayName.isNotBlank() && current.accountDisplayName != settings.accountDisplayName.orEmpty()) {
                current.accountDisplayName
            } else {
                settings.accountDisplayName.orEmpty()
            }
            current.copy(
                settings = settings,
                isLoading = false,
                message = message,
                isAutoDetectEnabled = settings.direction.isAutoDetect,
                syncEnabled = settings.syncEnabled,
                isSyncing = false,
                lastSyncDisplay = formatInstant(settings.lastSyncedAt),
                accountEmail = email,
                accountDisplayName = displayName,
                apiEndpoint = settings.apiEndpoint,
                apiEndpointError = null,
                isDiagnosticsRunning = false,
                selectedThemeMode = settings.themeMode,
                selectedAppLanguage = settings.appLanguage
            )
        }
    }

    private fun normalizeEndpoint(endpoint: String): String = endpoint.trim().removeSuffix("/")

    private fun validateEndpoint(endpoint: String): String? {
        if (endpoint.isBlank()) return null
        return if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) null
        else application.getString(R.string.settings_api_endpoint_validation_error)
    }

    private fun formatInstant(instant: Instant?): String? {
        return instant?.toLocalDateTime(TimeZone.currentSystemDefault())?.let { local ->
            "%04d-%02d-%02d %02d:%02d".format(local.year, local.monthNumber, local.dayOfMonth, local.hour, local.minute)
        }
    }
}

private fun String?.orElseEmpty(): String = this ?: ""


