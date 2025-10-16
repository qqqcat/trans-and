package com.example.translatorapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.UpdateDirectionUseCase
import com.example.translatorapp.domain.usecase.UpdateModelUseCase
import com.example.translatorapp.domain.usecase.UpdateOfflineFallbackUseCase
import com.example.translatorapp.domain.usecase.UpdateTelemetryConsentUseCase
import com.example.translatorapp.util.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val updateDirectionUseCase: UpdateDirectionUseCase,
    private val updateModelUseCase: UpdateModelUseCase,
    private val updateOfflineFallbackUseCase: UpdateOfflineFallbackUseCase,
    private val updateTelemetryConsentUseCase: UpdateTelemetryConsentUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = loadSettingsUseCase()
            _uiState.value = SettingsUiState(settings = settings, isLoading = false)
        }
    }

    fun onDirectionSelected(direction: LanguageDirection) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateDirectionUseCase(direction)
            val settings = loadSettingsUseCase()
            _uiState.value = _uiState.value.copy(settings = settings, message = "语言方向已更新")
        }
    }

    fun onModelSelected(profile: TranslationModelProfile) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateModelUseCase(profile)
            val settings = loadSettingsUseCase()
            _uiState.value = _uiState.value.copy(settings = settings, message = "模型已切换")
        }
    }

    fun onOfflineFallbackChanged(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateOfflineFallbackUseCase(enabled)
            val settings = loadSettingsUseCase()
            _uiState.value = _uiState.value.copy(settings = settings)
        }
    }

    fun onTelemetryChanged(consent: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateTelemetryConsentUseCase(consent)
            val settings = loadSettingsUseCase()
            _uiState.value = _uiState.value.copy(settings = settings)
        }
    }
}
