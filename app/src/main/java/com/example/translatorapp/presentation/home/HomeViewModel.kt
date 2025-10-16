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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = loadSettingsUseCase()
            _uiState.value = _uiState.value.copy(settings = settings, isLoading = false)
            startRealtimeSession(settings)
        }
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
            _uiState.value = _uiState.value.copy(sessionState = HomeUiState().sessionState)
        }
    }

    override fun onCleared() {
        viewModelScope.launch(dispatcherProvider.io) {
            stopRealtimeSession()
        }
        super.onCleared()
    }
}
