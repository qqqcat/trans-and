package com.example.translatorapp.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.usecase.ClearHistoryUseCase
import com.example.translatorapp.domain.usecase.ObserveHistoryUseCase
import com.example.translatorapp.util.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistoryUseCase: ObserveHistoryUseCase,
    private val clearHistoryUseCase: ClearHistoryUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            observeHistoryUseCase().collect { items ->
                _uiState.update { it.copy(history = items) }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(dispatcherProvider.io) {
            clearHistoryUseCase()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }
}
