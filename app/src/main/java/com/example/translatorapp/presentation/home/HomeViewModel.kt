package com.example.translatorapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.ObserveLiveTranscriptionUseCase
import com.example.translatorapp.domain.usecase.ObserveSessionStateUseCase
import com.example.translatorapp.domain.usecase.PersistHistoryUseCase
import com.example.translatorapp.domain.usecase.StartRealtimeSessionUseCase
import com.example.translatorapp.domain.usecase.ToggleMicrophoneUseCase
import com.example.translatorapp.domain.usecase.TranslateImageUseCase
import com.example.translatorapp.domain.usecase.TranslateTextUseCase
import com.example.translatorapp.domain.usecase.DetectLanguageUseCase
import com.example.translatorapp.domain.usecase.StopRealtimeSessionUseCase
import com.example.translatorapp.util.DispatcherProvider
import com.example.translatorapp.util.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeSessionState: ObserveSessionStateUseCase,
    private val observeLiveTranscription: ObserveLiveTranscriptionUseCase,
    private val startRealtimeSession: StartRealtimeSessionUseCase,
    private val stopRealtimeSession: StopRealtimeSessionUseCase,
    private val toggleMicrophoneUseCase: ToggleMicrophoneUseCase,
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val persistHistoryUseCase: PersistHistoryUseCase,
    private val translateTextUseCase: TranslateTextUseCase,
    private val translateImageUseCase: TranslateImageUseCase,
    private val detectLanguageUseCase: DetectLanguageUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentSettings: UserSettings? = null
    private var hasStartedSession = false
    private var settingsLoaded = false
    private var permissionChecked = false

    init {
        viewModelScope.launch {
            observeSessionState().collect { state ->
                _uiState.value = _uiState.value.copy(
                    sessionState = state,
                    isMicActive = state.isMicrophoneOpen,
                    errorMessage = state.errorMessage
                )
            }
        }
        viewModelScope.launch(dispatcherProvider.io) {
            observeLiveTranscription().collect { content ->
                persistHistoryUseCase(content)
                _uiState.value = _uiState.value.copy(
                    transcriptHistory = (_uiState.value.transcriptHistory + content).takeLast(50),
                    detectedLanguage = content.detectedSourceLanguage,
                    lastManualTranslation = content,
                    selectedInputMode = TranslationInputMode.Voice
                )
            }
        }
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = loadSettingsUseCase()
            currentSettings = settings
            settingsLoaded = true
            _uiState.update { it.copy(settings = settings) }
            finalizeInitialization()
        }
        refreshPermissionStatus()
    }

    fun onToggleMicrophone() {
        viewModelScope.launch(dispatcherProvider.io) {
            val isActive = toggleMicrophoneUseCase()
            _uiState.value = _uiState.value.copy(isMicActive = isActive)
        }
    }

    fun onStopSession() {
        viewModelScope.launch(dispatcherProvider.io) {
            stopRealtimeSession()
            hasStartedSession = false
            _uiState.value = _uiState.value.copy(sessionState = HomeUiState().sessionState)
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        permissionChecked = true
        _uiState.update { it.copy(isRecordAudioPermissionGranted = isGranted) }
        finalizeInitialization()
        if (isGranted) {
            maybeStartSession()
        } else {
            hasStartedSession = false
            viewModelScope.launch(dispatcherProvider.io) {
                stopRealtimeSession()
            }
        }
    }

    fun refreshPermissionStatus() {
        viewModelScope.launch(dispatcherProvider.io) {
            val hasPermission = permissionManager.hasRecordAudioPermission()
            permissionChecked = true
            _uiState.update { it.copy(isRecordAudioPermissionGranted = hasPermission) }
            finalizeInitialization()
            if (hasPermission) {
                maybeStartSession()
            } else {
                hasStartedSession = false
                stopRealtimeSession()
            }
        }
    }

    fun onTextInputChanged(text: String) {
        _uiState.update { it.copy(textInput = text, manualTranslationError = null) }
    }

    fun onTranslateText() {
        val text = _uiState.value.textInput.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(manualTranslationError = "请输入需要翻译的文本") }
            return
        }
        val settings = currentSettings ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isTranslatingText = true, manualTranslationError = null) }
            val direction = resolveDirectionForText(text)
            val result = runCatching {
                translateTextUseCase(text, direction, settings.translationProfile)
            }
            result.onSuccess { content ->
                _uiState.update {
                    it.copy(
                        transcriptHistory = (it.transcriptHistory + content).takeLast(50),
                        textInput = "",
                        isTranslatingText = false,
                        detectedLanguage = content.detectedSourceLanguage,
                        lastManualTranslation = content,
                        selectedInputMode = TranslationInputMode.Text,
                        manualTranslationError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isTranslatingText = false,
                        manualTranslationError = error.message ?: "文本翻译失败"
                    )
                }
            }
        }
    }

    fun onImageTranslationRequested(imageBytes: ByteArray, description: String?) {
        val settings = currentSettings ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isTranslatingImage = true, manualTranslationError = null) }
            val direction = settings.direction
            val result = runCatching {
                translateImageUseCase(imageBytes, direction, settings.translationProfile)
            }
            result.onSuccess { content ->
                val normalized = if (content.transcript.isBlank() && !description.isNullOrBlank()) {
                    content.copy(transcript = description)
                } else {
                    content
                }
                _uiState.update {
                    it.copy(
                        transcriptHistory = (it.transcriptHistory + normalized).takeLast(50),
                        isTranslatingImage = false,
                        detectedLanguage = normalized.detectedSourceLanguage,
                        lastManualTranslation = normalized,
                        selectedInputMode = TranslationInputMode.Image,
                        manualTranslationError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isTranslatingImage = false,
                        manualTranslationError = error.message ?: "图片翻译失败"
                    )
                }
            }
        }
    }

    fun onInputModeSelected(mode: TranslationInputMode) {
        _uiState.update { it.copy(selectedInputMode = mode) }
        if (mode == TranslationInputMode.Voice) {
            maybeStartSession()
        }
    }

    private fun finalizeInitialization() {
        if (settingsLoaded && permissionChecked) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun maybeStartSession() {
        val settings = currentSettings ?: return
        if (_uiState.value.isRecordAudioPermissionGranted && !hasStartedSession) {
            hasStartedSession = true
            viewModelScope.launch(dispatcherProvider.io) {
                startRealtimeSession(settings)
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(dispatcherProvider.io) {
            stopRealtimeSession()
            hasStartedSession = false
        }
        super.onCleared()
    }

    private suspend fun resolveDirectionForText(text: String): LanguageDirection {
        val settings = currentSettings ?: return UserSettings().direction
        return if (settings.direction.isAutoDetect) {
            _uiState.update { it.copy(detectedLanguage = null) }
            val detected = detectLanguageUseCase(text)
            if (detected != null) {
                _uiState.update { it.copy(detectedLanguage = detected) }
                settings.direction.withSource(detected)
            } else {
                settings.direction
            }
        } else {
            settings.direction
        }
    }
}
