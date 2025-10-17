package com.example.translatorapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.ObserveLiveTranscriptionUseCase
import com.example.translatorapp.domain.usecase.ObserveSessionStateUseCase
import com.example.translatorapp.domain.usecase.PersistHistoryUseCase
import com.example.translatorapp.domain.usecase.StartRealtimeSessionUseCase
import com.example.translatorapp.domain.usecase.ToggleMicrophoneUseCase
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
                    transcriptHistory = (_uiState.value.transcriptHistory + content).takeLast(20)
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
}
