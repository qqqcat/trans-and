package com.example.translatorapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.model.ThemeMode
import com.example.translatorapp.domain.usecase.ObserveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppPreferencesViewModel @Inject constructor(
    observeSettingsUseCase: ObserveSettingsUseCase
) : ViewModel() {

    private val settings = observeSettingsUseCase()

    val themeMode: StateFlow<ThemeMode> = settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)
}
