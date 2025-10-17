package com.example.translatorapp.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.translatorapp.BuildConfig
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.presentation.theme.LocalSpacing

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    paddingValues: PaddingValues
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        paddingValues = paddingValues,
        onAutoDetectChanged = viewModel::onAutoDetectChanged,
        onSourceLanguageSelected = viewModel::onSourceLanguageSelected,
        onTargetLanguageSelected = viewModel::onTargetLanguageSelected,
        onModelSelected = viewModel::onModelSelected,
        onOfflineFallbackChanged = viewModel::onOfflineFallbackChanged,
        onTelemetryChanged = viewModel::onTelemetryChanged,
        onAccountEmailChange = viewModel::onAccountEmailChange,
        onAccountDisplayNameChange = viewModel::onAccountDisplayNameChange,
        onSaveAccount = viewModel::onSaveAccount,
        onSyncToggle = viewModel::onSyncToggle,
        onSyncNow = viewModel::onSyncNow,
        onApiEndpointChange = viewModel::onApiEndpointChange,
        onSaveApiEndpoint = viewModel::onSaveApiEndpoint,
        onResetApiEndpoint = viewModel::onResetApiEndpoint
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    paddingValues: PaddingValues,
    onAutoDetectChanged: (Boolean) -> Unit,
    onSourceLanguageSelected: (SupportedLanguage) -> Unit,
    onTargetLanguageSelected: (SupportedLanguage) -> Unit,
    onModelSelected: (TranslationModelProfile) -> Unit,
    onOfflineFallbackChanged: (Boolean) -> Unit,
    onTelemetryChanged: (Boolean) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onAccountDisplayNameChange: (String) -> Unit,
    onSaveAccount: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onApiEndpointChange: (String) -> Unit,
    onSaveApiEndpoint: () -> Unit,
    onResetApiEndpoint: () -> Unit
) {
    val spacing = LocalSpacing.current

    if (state.isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        item {
            SettingsHeader(message = state.message)
        }

        item {
            SettingSection(
                title = stringResource(id = R.string.settings_section_language)
            ) {
                Text(
                    text = formatDirection(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingSwitchRow(
                    title = stringResource(id = R.string.settings_auto_detect_label),
                    checked = state.isAutoDetectEnabled,
                    onCheckedChange = onAutoDetectChanged
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = stringResource(id = R.string.settings_source_language_label),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    state.availableLanguages.forEach { language ->
                        FilterChip(
                            selected = state.settings.direction.sourceLanguage == language,
                            enabled = !state.isAutoDetectEnabled,
                            onClick = { onSourceLanguageSelected(language) },
                            label = { Text(text = language.displayName) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.md))
                Text(
                    text = stringResource(id = R.string.settings_target_language_label),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    state.availableLanguages.forEach { language ->
                        FilterChip(
                            selected = state.settings.direction.targetLanguage == language,
                            onClick = { onTargetLanguageSelected(language) },
                            label = { Text(text = language.displayName) }
                        )
                    }
                }
            }
        }

        item {
            SettingSection(title = stringResource(id = R.string.settings_section_model)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    state.availableModels.forEach { model ->
                        FilterChip(
                            selected = state.settings.translationProfile == model,
                            onClick = { onModelSelected(model) },
                            label = { Text(text = model.displayName) }
                        )
                    }
                }
            }
        }

        item {
            SettingSection(title = stringResource(id = R.string.settings_section_network)) {
                OutlinedTextField(
                    value = state.apiEndpoint,
                    onValueChange = onApiEndpointChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(id = R.string.settings_api_endpoint_label)) },
                    placeholder = { Text(text = stringResource(id = R.string.settings_api_endpoint_hint)) },
                    supportingText = {
                        val helper = state.apiEndpointError ?: stringResource(
                            id = R.string.settings_api_endpoint_default,
                            BuildConfig.REALTIME_BASE_URL
                        )
                        Text(
                            text = helper,
                            color = if (state.apiEndpointError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = state.apiEndpointError != null,
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    OutlinedButton(onClick = onSaveApiEndpoint) {
                        Text(text = stringResource(id = R.string.settings_api_endpoint_save))
                    }
                    TextButton(onClick = onResetApiEndpoint) {
                        Text(text = stringResource(id = R.string.settings_api_endpoint_reset))
                    }
                }
                HorizontalDivider()
                SettingSwitchRow(
                    title = stringResource(id = R.string.settings_offline_fallback_label),
                    checked = state.settings.offlineFallbackEnabled,
                    onCheckedChange = onOfflineFallbackChanged
                )
            }
        }

        item {
            SettingSection(title = stringResource(id = R.string.settings_section_preferences)) {
                SettingSwitchRow(
                    title = stringResource(id = R.string.settings_telemetry_label),
                    checked = state.settings.allowTelemetry,
                    onCheckedChange = onTelemetryChanged
                )
            }
        }

        item {
            SettingSection(title = stringResource(id = R.string.settings_section_account)) {
                OutlinedTextField(
                    value = state.accountEmail,
                    onValueChange = onAccountEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(id = R.string.settings_account_email)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.accountDisplayName,
                    onValueChange = onAccountDisplayNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(id = R.string.settings_account_display_name)) },
                    singleLine = true
                )
                Button(onClick = onSaveAccount, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(id = R.string.settings_save_account))
                }
                SettingSwitchRow(
                    title = stringResource(id = R.string.settings_sync_toggle),
                    checked = state.syncEnabled,
                    onCheckedChange = onSyncToggle
                )
                Text(
                    text = state.lastSyncDisplay?.let { stringResource(id = R.string.settings_last_sync, it) }
                        ?: stringResource(id = R.string.settings_sync_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.syncEnabled && !state.isSyncing
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = spacing.sm),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(text = stringResource(id = R.string.settings_sync_now))
                }
            }
        }

        item {
            SettingSection(title = "版本信息") {
                Text(
                    text = "API Host: ${state.settings.apiEndpoint.ifBlank { BuildConfig.REALTIME_BASE_URL }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Build: ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(message: String?) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium
        )
        AnimatedVisibility(visible = !message.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = message.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatDirection(state: SettingsUiState): String {
    val source = state.settings.direction.sourceLanguage?.displayName ?: "自动检测"
    val target = state.settings.direction.targetLanguage.displayName
    return "$source → $target"
}
