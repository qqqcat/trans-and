package com.example.translatorapp.presentation.settings

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.translatorapp.BuildConfig
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.ThemeMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.presentation.LocaleManager
import com.example.translatorapp.presentation.components.LanguagePickerSheet
import com.example.translatorapp.presentation.components.LanguagePickerTarget
import com.example.translatorapp.presentation.theme.LocalGradients
import com.example.translatorapp.presentation.theme.LocalSpacing
import com.example.translatorapp.presentation.theme.WindowBreakpoint
import com.example.translatorapp.presentation.theme.cardPadding
import com.example.translatorapp.presentation.theme.computeBreakpoint
import com.example.translatorapp.presentation.theme.horizontalPadding
import com.example.translatorapp.presentation.theme.sectionSpacing
import java.util.Locale



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
        onDirectionSelected = viewModel::onDirectionSelected,
        onSourceLanguageSelected = viewModel::onSourceLanguageSelected,
        onTargetLanguageSelected = viewModel::onTargetLanguageSelected,
        onModelSelected = viewModel::onModelSelected,
        onRunMicrophoneTest = viewModel::onRunMicrophoneTest,
        onRunTtsTest = viewModel::onRunTtsTest,
        onTelemetryChanged = viewModel::onTelemetryChanged,
        onAccountEmailChange = viewModel::onAccountEmailChange,
        onAccountDisplayNameChange = viewModel::onAccountDisplayNameChange,
        onSaveAccount = viewModel::onSaveAccount,
        onSyncToggle = viewModel::onSyncToggle,
        onSyncNow = viewModel::onSyncNow,
        onApiEndpointChange = viewModel::onApiEndpointChange,
        onSaveApiEndpoint = viewModel::onSaveApiEndpoint,
        onResetApiEndpoint = viewModel::onResetApiEndpoint,
        onThemeModeSelected = viewModel::onThemeModeSelected
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    paddingValues: PaddingValues,
    onAutoDetectChanged: (Boolean) -> Unit,
    onDirectionSelected: (LanguageDirection) -> Unit,
    onSourceLanguageSelected: (SupportedLanguage) -> Unit,
    onTargetLanguageSelected: (SupportedLanguage) -> Unit,
    onModelSelected: (TranslationModelProfile) -> Unit,
    onRunMicrophoneTest: () -> Unit,
    onRunTtsTest: () -> Unit,
    onTelemetryChanged: (Boolean) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onAccountDisplayNameChange: (String) -> Unit,
    onSaveAccount: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onApiEndpointChange: (String) -> Unit,
    onSaveApiEndpoint: () -> Unit,
    onResetApiEndpoint: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val breakpoint = computeBreakpoint(maxWidth)
        val horizontalPadding = spacing.horizontalPadding(breakpoint)
        val verticalSpacing = spacing.sectionSpacing(breakpoint)
        val cardPadding = spacing.cardPadding(breakpoint)

        var pickerTarget by rememberSaveable { mutableStateOf<LanguagePickerTarget?>(null) }
        var favoriteDirectionKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
        var networkExpanded by rememberSaveable { mutableStateOf(false) }
        var accountExpanded by rememberSaveable { mutableStateOf(false) }
        var activeLanguageTab by rememberSaveable { mutableStateOf(LanguagePickerTarget.Source) }
        var modelPickerVisible by rememberSaveable { mutableStateOf(false) }

        val favoriteDirections = remember(favoriteDirectionKeys) {
            favoriteDirectionKeys.distinct().map { LanguageDirection.decode(it) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = verticalSpacing,
                bottom = verticalSpacing
            ),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            item { SettingsHeader(message = state.message) }

            item {
                SettingSection(
                    title = stringResource(id = R.string.settings_section_language),
                    contentPadding = cardPadding
                ) {
                    LanguageSettingsCard(
                        state = state,
                        activeTab = activeLanguageTab,
                        favoriteDirections = favoriteDirections,
                        favoriteDirectionKeys = favoriteDirectionKeys,
                        onTabChange = { activeLanguageTab = it },
                        onAutoDetectChanged = onAutoDetectChanged,
                        onToggleFavorite = { key ->
                            favoriteDirectionKeys = toggleFavorite(favoriteDirectionKeys, key)
                        },
                        onRequestPicker = { target -> pickerTarget = target },
                        onFavoriteDirectionSelected = onDirectionSelected
                    )
                }
            }

            item {
                SettingSection(
                    title = stringResource(id = R.string.settings_section_model),
                    contentPadding = cardPadding
                ) {
                    ModelSelectionCard(
                        currentModel = state.settings.translationProfile,
                        onOpenPicker = { modelPickerVisible = true }
                    )
                }
            }

            item {
                SettingSection(
                    title = stringResource(id = R.string.settings_section_diagnostics),
                    contentPadding = cardPadding
                ) {
                    DiagnosticsCard(
                        isRunning = state.isDiagnosticsRunning,
                        onRunMicrophoneTest = onRunMicrophoneTest,
                        onRunTtsTest = onRunTtsTest
                    )
                }
            }

            item {
                ExpandableSettingSection(
                    title = stringResource(id = R.string.settings_section_network),
                    summary = networkSummary(state),
                    expanded = networkExpanded,
                    onToggle = { networkExpanded = !networkExpanded }
                ) {
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
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.apiEndpointError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        isError = state.apiEndpointError != null
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onResetApiEndpoint) {
                            Text(text = stringResource(id = R.string.settings_api_endpoint_reset))
                        }
                        Spacer(modifier = Modifier.width(spacing.sm))
                        OutlinedButton(onClick = onSaveApiEndpoint) {
                            Text(text = stringResource(id = R.string.settings_api_endpoint_save))
                        }
                    }
                }
            }

            item {
                SettingSection(
                    title = stringResource(id = R.string.settings_section_theme),
                    contentPadding = cardPadding
                ) {
                    ThemeModeCard(
                        selectedMode = state.selectedThemeMode,
                        onModeSelected = onThemeModeSelected
                    )
                }
            }

            item {
                SettingSection(
                    title = stringResource(id = R.string.settings_section_language_note),
                    contentPadding = cardPadding
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_language_system_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingSection(
                    title = stringResource(id = R.string.settings_section_preferences),
                    contentPadding = cardPadding
                ) {
                    SettingToggleRow(
                        title = stringResource(id = R.string.settings_telemetry_label),
                        checked = state.settings.allowTelemetry,
                        onCheckedChange = onTelemetryChanged
                    )
                }
            }

            item {
                ExpandableSettingSection(
                    title = stringResource(id = R.string.settings_section_account),
                    summary = accountSummary(state),
                    expanded = accountExpanded,
                    onToggle = { accountExpanded = !accountExpanded }
                ) {
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
                    SettingToggleRow(
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
                SettingSection(
                    title = stringResource(id = R.string.settings_section_version),
                    contentPadding = cardPadding
                ) {
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

        LanguagePickerSheet(
            target = pickerTarget,
            currentDirection = state.settings.direction,
            availableLanguages = state.availableLanguages,
            favorites = favoriteDirections,
            isAutoDetectEnabled = state.isAutoDetectEnabled,
            onDismiss = { pickerTarget = null },
            onFavoriteSelected = { direction ->
                onDirectionSelected(direction)
                pickerTarget = null
            },
            onSourceSelected = { language ->
                if (language == null) {
                    if (!state.isAutoDetectEnabled) {
                        onAutoDetectChanged(true)
                    }
                } else {
                    if (state.isAutoDetectEnabled) {
                        onAutoDetectChanged(false)
                    }
                    onSourceLanguageSelected(language)
                }
                pickerTarget = null
            },
            onTargetSelected = { language ->
                onTargetLanguageSelected(language)
                pickerTarget = null
            },
            onTargetChange = { pickerTarget = it }
        )

        ModelPickerSheet(
            visible = modelPickerVisible,
            models = state.availableModels,
            current = state.settings.translationProfile,
            onDismiss = { modelPickerVisible = false },
            onModelSelected = {
                onModelSelected(it)
                modelPickerVisible = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSettingsCard(
    state: SettingsUiState,
    activeTab: LanguagePickerTarget,
    favoriteDirections: List<LanguageDirection>,
    favoriteDirectionKeys: List<String>,
    onTabChange: (LanguagePickerTarget) -> Unit,
    onAutoDetectChanged: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRequestPicker: (LanguagePickerTarget) -> Unit,
    onFavoriteDirectionSelected: (LanguageDirection) -> Unit
) {
    val spacing = LocalSpacing.current
    val direction = state.settings.direction
    val autoLabel = stringResource(id = R.string.settings_auto_detect_label)
    val sourceLabel = autoDetectLabelIfNeeded(direction, autoLabel)
    val targetLabel = direction.targetLanguage.displayName
    val currentKey = direction.encode()
    val isFavorite = favoriteDirectionKeys.contains(currentKey)
    val favoriteDescription = if (isFavorite) {
        stringResource(id = R.string.settings_language_remove_favorite)
    } else {
        stringResource(id = R.string.settings_language_add_favorite)
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SettingsLanguageToggleRow(
            activeTab = activeTab,
            onTabChange = onTabChange
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRequestPicker(activeTab) },
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_language_tab_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(text = "→", style = MaterialTheme.typography.titleMedium)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_language_tab_target),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                IconButton(onClick = { onToggleFavorite(currentKey) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = favoriteDescription,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (activeTab == LanguagePickerTarget.Source) {
            SettingToggleRow(
                title = stringResource(id = R.string.settings_auto_detect_label),
                checked = state.isAutoDetectEnabled,
                onCheckedChange = onAutoDetectChanged
            )
            OutlinedButton(
                onClick = { onRequestPicker(LanguagePickerTarget.Source) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.settings_language_select_source, sourceLabel))
            }
        } else {
            OutlinedButton(
                onClick = { onRequestPicker(LanguagePickerTarget.Target) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.settings_language_select_target, targetLabel))
            }
        }

        if (favoriteDirections.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = stringResource(id = R.string.settings_language_favorites_section),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    favoriteDirections.forEach { favorite ->
                        AssistChip(
                            onClick = { onFavoriteDirectionSelected(favorite) },
                            label = { Text(text = formatDirection(favorite, autoLabel)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLanguageToggleRow(
    activeTab: LanguagePickerTarget,
    onTabChange: (LanguagePickerTarget) -> Unit
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        SettingsLanguageToggleButton(
            label = stringResource(id = R.string.settings_language_tab_source),
            selected = activeTab == LanguagePickerTarget.Source,
            modifier = Modifier.weight(1f),
            onClick = { onTabChange(LanguagePickerTarget.Source) }
        )
        SettingsLanguageToggleButton(
            label = stringResource(id = R.string.settings_language_tab_target),
            selected = activeTab == LanguagePickerTarget.Target,
            modifier = Modifier.weight(1f),
            onClick = { onTabChange(LanguagePickerTarget.Target) }
        )
    }
}

@Composable
private fun SettingsLanguageToggleButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp)
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ModelSelectionCard(

    currentModel: TranslationModelProfile,
    onOpenPicker: () -> Unit
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(
            text = stringResource(id = R.string.home_model_label, currentModel.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onOpenPicker) {
            Text(text = stringResource(id = R.string.settings_model_open_picker))
        }
    }
}

@Composable
private fun DiagnosticsCard(
    isRunning: Boolean,
    onRunMicrophoneTest: () -> Unit,
    onRunTtsTest: () -> Unit
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(id = R.string.settings_diagnostics_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            OutlinedButton(
                onClick = onRunMicrophoneTest,
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.settings_diagnostics_microphone))
            }
            OutlinedButton(
                onClick = onRunTtsTest,
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.settings_diagnostics_tts))
            }
        }
        if (isRunning) {
            Text(
                text = stringResource(id = R.string.settings_diagnostics_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    visible: Boolean,
    models: List<TranslationModelProfile>,
    current: TranslationModelProfile,
    onDismiss: () -> Unit,
    onModelSelected: (TranslationModelProfile) -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_model_picker_title),
                style = MaterialTheme.typography.titleMedium
            )
            models.forEach { model ->
                val selected = model == current
                ListItem(
                    headlineContent = { Text(text = model.displayName) },
                    leadingContent = {
                        RadioButton(selected = selected, onClick = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModelSelected(model) },
                    colors = ListItemDefaults.colors(),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                )
            }
        }
    }
}
@Composable
private fun ExpandableSettingSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalSpacing.current
    val gradients = LocalGradients.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradients.card)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.md, end = spacing.md, bottom = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    content()
                }
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
    contentPadding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalSpacing.current
    val gradients = LocalGradients.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradients.card)
                .padding(contentPadding),
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
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun toggleFavorite(current: List<String>, key: String): List<String> {
    return if (current.contains(key)) {
        current.filterNot { it == key }
    } else {
        current + key
    }
}

private fun networkSummary(state: SettingsUiState): String {
    val api = state.settings.apiEndpoint.ifBlank { BuildConfig.REALTIME_BASE_URL }
    return "API: $api"
}

private fun accountSummary(state: SettingsUiState): String {
    val email = state.settings.accountEmail?.takeIf { it.isNotBlank() } ?: "未设置邮箱"
    val sync = if (state.syncEnabled) "同步开启" else "同步关闭"
    return "$email · $sync"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeModeCard(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(
            text = stringResource(id = R.string.settings_theme_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                OutlinedButton(
                    onClick = { onModeSelected(mode) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                ) {
                    Text(
                        text = themeModeLabel(mode),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> stringResource(id = R.string.settings_theme_system)
    ThemeMode.Light -> stringResource(id = R.string.settings_theme_light)
    ThemeMode.Dark -> stringResource(id = R.string.settings_theme_dark)
}





private fun autoDetectLabelIfNeeded(direction: LanguageDirection, autoLabel: String): String {
    return direction.sourceLanguage?.displayName ?: autoLabel
}

private fun formatDirection(direction: LanguageDirection, autoLabel: String): String {
    return "${autoDetectLabelIfNeeded(direction, autoLabel)} -> ${direction.targetLanguage.displayName}"
}



private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}






