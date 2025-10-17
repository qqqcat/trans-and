package com.example.translatorapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.UpdateDirectionUseCase
import com.example.translatorapp.domain.usecase.UpdateModelUseCase
import com.example.translatorapp.domain.usecase.UpdateOfflineFallbackUseCase
import com.example.translatorapp.domain.usecase.UpdateTelemetryConsentUseCase
import com.example.translatorapp.domain.usecase.UpdateAccountProfileUseCase
import com.example.translatorapp.domain.usecase.UpdateSyncEnabledUseCase
import com.example.translatorapp.domain.usecase.SyncAccountUseCase
import com.example.translatorapp.util.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val updateDirectionUseCase: UpdateDirectionUseCase,
    private val updateModelUseCase: UpdateModelUseCase,
    private val updateOfflineFallbackUseCase: UpdateOfflineFallbackUseCase,
    private val updateTelemetryConsentUseCase: UpdateTelemetryConsentUseCase,
    private val updateAccountProfileUseCase: UpdateAccountProfileUseCase,
    private val updateSyncEnabledUseCase: UpdateSyncEnabledUseCase,
    private val syncAccountUseCase: SyncAccountUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var manualSourceLanguage: SupportedLanguage = SupportedLanguage.ChineseSimplified

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            refreshSettings(preserveAccountInputs = false)
        }
    }

    fun onDirectionSelected(direction: LanguageDirection) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateDirectionUseCase(direction)
            refreshSettings(message = "语言方向已更新")
        }
    }

    fun onSourceLanguageSelected(language: SupportedLanguage) {
        manualSourceLanguage = language
        viewModelScope.launch(dispatcherProvider.io) {
            val current = loadSettingsUseCase()
            if (!current.direction.isAutoDetect) {
                updateDirectionUseCase(current.direction.withSource(language))
            }
            refreshSettings(message = "源语言已更新")
        }
    }

    fun onTargetLanguageSelected(language: SupportedLanguage) {
        viewModelScope.launch(dispatcherProvider.io) {
            val current = loadSettingsUseCase()
            val updatedDirection = current.direction.withTarget(language)
            updateDirectionUseCase(updatedDirection)
            refreshSettings(message = "目标语言已更新")
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
            refreshSettings(message = "自动检测已更新")
        }
    }

    fun onModelSelected(profile: TranslationModelProfile) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateModelUseCase(profile)
            refreshSettings(message = "模型已切换")
        }
    }

    fun onOfflineFallbackChanged(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateOfflineFallbackUseCase(enabled)
            refreshSettings()
        }
    }

    fun onTelemetryChanged(consent: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateTelemetryConsentUseCase(consent)
            refreshSettings()
        }
    }

    fun onAccountEmailChange(value: String) {
        _uiState.update { it.copy(accountEmail = value) }
    }

    fun onAccountDisplayNameChange(value: String) {
        _uiState.update { it.copy(accountDisplayName = value) }
    }

    fun onSaveAccount() {
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = loadSettingsUseCase()
            val accountId = settings.accountId ?: java.util.UUID.randomUUID().toString()
            val email = uiState.value.accountEmail
            if (email.isBlank()) {
                _uiState.update { it.copy(message = "请输入有效邮箱") }
                return@launch
            }
            updateAccountProfileUseCase(
                AccountProfile(
                    accountId = accountId,
                    email = email,
                    displayName = uiState.value.accountDisplayName.ifBlank { null }
                )
            )
            refreshSettings(message = "账号信息已更新", preserveAccountInputs = false)
        }
    }

    fun onSyncToggle(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateSyncEnabledUseCase(enabled)
            refreshSettings()
        }
    }

    fun onSyncNow() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isSyncing = true, message = null) }
            val result = syncAccountUseCase()
            refreshSettings(
                message = result.message ?: if (result.success) "同步成功" else "同步失败"
            )
        }
    }

    private suspend fun refreshSettings(message: String? = null, preserveAccountInputs: Boolean = true) {
        val settings = loadSettingsUseCase()
        manualSourceLanguage = settings.direction.sourceLanguage ?: manualSourceLanguage
        _uiState.update { current ->
            val email = if (preserveAccountInputs && current.accountEmail.isNotBlank() && current.accountEmail != settings.accountEmail.orElseEmpty()) {
                current.accountEmail
            } else {
                settings.accountEmail.orElseEmpty()
            }
            val displayName = if (preserveAccountInputs && current.accountDisplayName.isNotBlank() && current.accountDisplayName != settings.accountDisplayName.orElseEmpty()) {
                current.accountDisplayName
            } else {
                settings.accountDisplayName.orElseEmpty()
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
                accountDisplayName = displayName
            )
        }
    }

    private fun formatInstant(instant: Instant?): String? {
        return instant?.toLocalDateTime(TimeZone.currentSystemDefault())?.let { local ->
            "%04d-%02d-%02d %02d:%02d".format(local.year, local.monthNumber, local.dayOfMonth, local.hour, local.minute)
        }
    }
}

private fun String?.orElseEmpty(): String = this ?: ""
}
