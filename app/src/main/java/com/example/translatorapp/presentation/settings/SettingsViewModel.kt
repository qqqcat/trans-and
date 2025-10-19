package com.example.translatorapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.SyncAccountUseCase
import com.example.translatorapp.domain.usecase.UpdateAccountProfileUseCase
import com.example.translatorapp.domain.usecase.UpdateApiEndpointUseCase
import com.example.translatorapp.domain.usecase.UpdateDirectionUseCase
import com.example.translatorapp.domain.usecase.UpdateModelUseCase
import com.example.translatorapp.domain.usecase.UpdateOfflineFallbackUseCase
import com.example.translatorapp.domain.usecase.UpdateSyncEnabledUseCase
import com.example.translatorapp.domain.usecase.UpdateTelemetryConsentUseCase
import com.example.translatorapp.util.DispatcherProvider
import com.example.translatorapp.offline.DiagnosticsManager
import com.example.translatorapp.offline.OfflineModelController
import com.example.translatorapp.offline.OfflineModelProfile
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
    private val updateApiEndpointUseCase: UpdateApiEndpointUseCase,
    private val offlineModelController: OfflineModelController,
    private val diagnosticsManager: DiagnosticsManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var manualSourceLanguage: SupportedLanguage = SupportedLanguage.ChineseSimplified

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            refreshSettings(preserveInputs = false)
        }
        viewModelScope.launch {
            offlineModelController.state.collect { offline ->
                _uiState.update { it.copy(offlineState = offline) }
            }
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
                refreshSettings(message = "源语言已更新")
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

            refreshSettings(message = if (enabled) "已开启自动检测" else "已切换为手动选择")

        }

    }



    fun onModelSelected(profile: TranslationModelProfile) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateModelUseCase(profile)
            refreshSettings(message = "模型已切换为 ${profile.displayName}")
        }
    }

    fun onDownloadOfflineModel(profile: OfflineModelProfile) {
        viewModelScope.launch(dispatcherProvider.io) {
            val label = profile.displayLabel()
            runCatching { offlineModelController.ensureModel(profile) }
                .onSuccess { refreshSettings(message = "$label 已准备就绪") }
                .onFailure { throwable ->
                    _uiState.update { it.copy(message = "$label 下载失败：${throwable.userMessage()}") }
                }
        }
    }

    fun onRunMicrophoneTest() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(message = "正在检测麦克风...", isDiagnosticsRunning = true) }
            val result = runCatching { diagnosticsManager.runMicrophoneTest() }
                .map { (it * 100).toInt() }
            val message = result.fold(
                onSuccess = { level ->
                    if (level > 5) "麦克风检测通过，平均幅值约 ${level}%" else "麦克风检测完成，但信号偏弱 (约 ${level}%)"
                },
                onFailure = { error -> error.userMessage() }
            )
            _uiState.update { it.copy(message = message, isDiagnosticsRunning = false) }
        }
    }

    fun onRunTtsTest() {
        viewModelScope.launch(dispatcherProvider.io) {
            val language = runCatching { loadSettingsUseCase().direction.targetLanguage }.getOrNull()
            if (language == null) {
                _uiState.update { it.copy(message = "无法确定目标语言，请先在设置里选择翻译语言") }
                return@launch
            }
            _uiState.update { it.copy(message = "正在检测 TTS...", isDiagnosticsRunning = true) }
            val passed = runCatching { diagnosticsManager.runTtsTest(language) }.getOrElse { error ->
                _uiState.update { it.copy(message = error.userMessage(), isDiagnosticsRunning = false) }
                return@launch
            }
            val message = if (passed) "系统 TTS 可用，语音合成正常" else "TTS 合成失败，请检查系统语音包"
            _uiState.update { it.copy(message = message, isDiagnosticsRunning = false) }
        }
    }

    fun onRemoveOfflineModel(profile: OfflineModelProfile) {
        viewModelScope.launch(dispatcherProvider.io) {
            val label = profile.displayLabel()
            runCatching { offlineModelController.removeModel(profile) }
                .onSuccess { refreshSettings(message = "$label 已移除") }
                .onFailure { throwable ->
                    _uiState.update { it.copy(message = "$label 移除失败：${throwable.userMessage()}") }
                }
        }
    }

    fun onOfflineFallbackChanged(enabled: Boolean) {

        viewModelScope.launch(dispatcherProvider.io) {

            updateOfflineFallbackUseCase(enabled)

            refreshSettings(message = if (enabled) "已开启离线兜底" else "已关闭离线兜底")

        }

    }



    fun onTelemetryChanged(consent: Boolean) {

        viewModelScope.launch(dispatcherProvider.io) {

            updateTelemetryConsentUseCase(consent)

            refreshSettings(message = if (consent) "已开启数据共享" else "已关闭数据共享")

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
            refreshSettings(message = if (raw.isBlank()) "已恢复默认服务地址" else "服务地址已更新")
        }
    }

    fun onResetApiEndpoint() {
        viewModelScope.launch(dispatcherProvider.io) {
            updateApiEndpointUseCase("")
            refreshSettings(message = "已恢复默认服务地址")
        }
    }

    fun onSaveAccount() {
        val email = uiState.value.accountEmail.trim()
        if (email.isEmpty()) {
            _uiState.update { it.copy(message = "请输入有效邮箱") }
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
            refreshSettings(message = "账户信息已更新", preserveInputs = false)
        }
    }

    fun onSyncToggle(enabled: Boolean) {

        viewModelScope.launch(dispatcherProvider.io) {

            updateSyncEnabledUseCase(enabled)

            refreshSettings(message = if (enabled) "已开启同步" else "已关闭同步")

        }

    }



    fun onSyncNow() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isSyncing = true, message = null) }
            val result = syncAccountUseCase()
            val message = result.message ?: if (result.success) "同步完成" else "同步失败"
            refreshSettings(message = message)
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
                isDiagnosticsRunning = false
            )
        }
    }

    private fun normalizeEndpoint(endpoint: String): String = endpoint.trim().removeSuffix("/")

    private fun validateEndpoint(endpoint: String): String? {
        if (endpoint.isBlank()) return null
        return if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) null
        else "请输入以 http(s):// 开头的有效地址，或留空使用默认服务。"
    }

    private fun formatInstant(instant: Instant?): String? {
        return instant?.toLocalDateTime(TimeZone.currentSystemDefault())?.let { local ->
            "%04d-%02d-%02d %02d:%02d".format(local.year, local.monthNumber, local.dayOfMonth, local.hour, local.minute)
        }
    }
}

private fun String?.orElseEmpty(): String = this ?: ""

private fun OfflineModelProfile.displayLabel(): String = when (this) {
    OfflineModelProfile.Tiny -> "Whisper Tiny"
    OfflineModelProfile.Turbo -> "Whisper Turbo"
}

private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "未知错误"

