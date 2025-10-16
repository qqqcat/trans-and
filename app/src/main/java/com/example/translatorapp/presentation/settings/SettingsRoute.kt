package com.example.translatorapp.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationModelProfile

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    paddingValues: PaddingValues,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsScreen(
        state = uiState,
        paddingValues = paddingValues,
        onBack = onBack,
        onDirectionSelected = viewModel::onDirectionSelected,
        onModelSelected = viewModel::onModelSelected,
        onOfflineFallbackChanged = viewModel::onOfflineFallbackChanged,
        onTelemetryChanged = viewModel::onTelemetryChanged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onDirectionSelected: (LanguageDirection) -> Unit,
    onModelSelected: (TranslationModelProfile) -> Unit,
    onOfflineFallbackChanged: (Boolean) -> Unit,
    onTelemetryChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "方向选择")
        FlowRow(items = state.availableDirections, selected = state.settings.direction, onSelected = onDirectionSelected) {
            it.displayName()
        }
        Text(text = "模型选择")
        FlowRow(items = state.availableModels, selected = state.settings.translationProfile, onSelected = onModelSelected) {
            it.displayName()
        }
        SwitchRow(
            title = "离线兜底",
            checked = state.settings.offlineFallbackEnabled,
            onCheckedChange = onOfflineFallbackChanged
        )
        SwitchRow(
            title = "匿名会话性能上报",
            checked = state.settings.allowTelemetry,
            onCheckedChange = onTelemetryChanged
        )
        Button(onClick = onBack) {
            Text(text = "返回")
        }
        state.message?.let { Text(text = it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowRow(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    labelProvider: (T) -> String
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelected(item) },
                label = { Text(text = labelProvider(item)) }
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun LanguageDirection.displayName(): String = when (this) {
    LanguageDirection.ChineseToFrench -> "中文 → 法语"
    LanguageDirection.FrenchToChinese -> "法语 → 中文"
}

private fun TranslationModelProfile.displayName(): String = when (this) {
    TranslationModelProfile.Balanced -> "GPT-4o mini"
    TranslationModelProfile.Accuracy -> "GPT-4.1"
    TranslationModelProfile.Offline -> "Whisper v3"
}
