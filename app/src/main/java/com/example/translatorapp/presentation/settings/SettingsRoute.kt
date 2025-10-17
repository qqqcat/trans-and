package com.example.translatorapp.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.translatorapp.domain.model.SupportedLanguage
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
        onSourceLanguageSelected = viewModel::onSourceLanguageSelected,
        onTargetLanguageSelected = viewModel::onTargetLanguageSelected,
        onAutoDetectChanged = viewModel::onAutoDetectChanged,
        onModelSelected = viewModel::onModelSelected,
        onOfflineFallbackChanged = viewModel::onOfflineFallbackChanged,
        onTelemetryChanged = viewModel::onTelemetryChanged,
        onAccountEmailChange = viewModel::onAccountEmailChange,
        onAccountDisplayNameChange = viewModel::onAccountDisplayNameChange,
        onSaveAccount = viewModel::onSaveAccount,
        onSyncToggle = viewModel::onSyncToggle,
        onSyncNow = viewModel::onSyncNow
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onSourceLanguageSelected: (SupportedLanguage) -> Unit,
    onTargetLanguageSelected: (SupportedLanguage) -> Unit,
    onAutoDetectChanged: (Boolean) -> Unit,
    onModelSelected: (TranslationModelProfile) -> Unit,
    onOfflineFallbackChanged: (Boolean) -> Unit,
    onTelemetryChanged: (Boolean) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onAccountDisplayNameChange: (String) -> Unit,
    onSaveAccount: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "语言方向")
        SwitchRow(
            title = "自动检测源语言",
            checked = state.isAutoDetectEnabled,
            onCheckedChange = onAutoDetectChanged
        )
        Text(text = "选择源语言")
        LanguageFlowRow(
            languages = state.availableLanguages,
            selected = state.settings.direction.sourceLanguage ?: state.availableLanguages.first(),
            enabled = !state.isAutoDetectEnabled,
            onSelected = onSourceLanguageSelected
        )
        Text(text = "选择目标语言")
        LanguageFlowRow(
            languages = state.availableLanguages,
            selected = state.settings.direction.targetLanguage,
            onSelected = onTargetLanguageSelected
        )
        Text(text = "模型选择")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.availableModels.forEach { model ->
            FilterChip(
                    selected = model == state.settings.translationProfile,
                    onClick = { onModelSelected(model) },
                    label = { Text(text = model.displayName()) }
                )
            }
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
        Text(text = "账号信息")
        OutlinedTextField(
            value = state.accountEmail,
            onValueChange = onAccountEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "企业邮箱") }
        )
        OutlinedTextField(
            value = state.accountDisplayName,
            onValueChange = onAccountDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "显示名称") }
        )
        Button(onClick = onSaveAccount) {
            Text(text = "保存账号信息")
        }
        SwitchRow(
            title = "启用历史同步",
            checked = state.syncEnabled,
            onCheckedChange = onSyncToggle
        )
        Button(onClick = onSyncNow, enabled = state.syncEnabled && !state.isSyncing) {
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(text = "立即同步")
            }
        }
        state.lastSyncDisplay?.let {
            Text(text = "上次同步时间：$it")
        }
        Button(onClick = onBack) {
            Text(text = "返回")
        }
        state.message?.let { Text(text = it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageFlowRow(
    languages: List<SupportedLanguage>,
    selected: SupportedLanguage,
    enabled: Boolean = true,
    onSelected: (SupportedLanguage) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        languages.forEach { language ->
            FilterChip(
                selected = language == selected,
                onClick = { if (enabled) onSelected(language) },
                enabled = enabled,
                label = { Text(text = language.displayName) }
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

private fun TranslationModelProfile.displayName(): String = when (this) {
    TranslationModelProfile.Balanced -> "GPT-4o mini"
    TranslationModelProfile.Accuracy -> "GPT-4.1"
    TranslationModelProfile.Offline -> "Whisper v3"
}
