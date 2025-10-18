package com.example.translatorapp.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
enum class LanguagePickerTarget {
    Source,
    Target
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    target: LanguagePickerTarget?,
    currentDirection: LanguageDirection,
    availableLanguages: List<SupportedLanguage>,
    favorites: List<LanguageDirection>,
    isAutoDetectEnabled: Boolean,
    onDismiss: () -> Unit,
    onFavoriteSelected: (LanguageDirection) -> Unit,
    onSourceSelected: (SupportedLanguage?) -> Unit,
    onTargetSelected: (SupportedLanguage) -> Unit,
    onTargetChange: ((LanguagePickerTarget) -> Unit)? = null
) {
    if (target == null) return
    var searchQuery by rememberSaveable(target) { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filteredLanguages = remember(searchQuery, availableLanguages) {
        if (searchQuery.isBlank()) {
            availableLanguages
        } else {
            availableLanguages.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = {
            searchQuery = ""
            onDismiss()
        },
        sheetState = sheetState
    ) {
        LanguagePickerContent(
            target = target,
            currentDirection = currentDirection,
            favorites = favorites,
            languages = filteredLanguages,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isAutoDetectEnabled = isAutoDetectEnabled,
            onFavoriteSelected = onFavoriteSelected,
            onSourceSelected = onSourceSelected,
            onTargetSelected = onTargetSelected,
            onTargetChange = onTargetChange
        )
    }
}

@Composable
private fun LanguagePickerContent(
    target: LanguagePickerTarget,
    currentDirection: LanguageDirection,
    favorites: List<LanguageDirection>,
    languages: List<SupportedLanguage>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isAutoDetectEnabled: Boolean,
    onFavoriteSelected: (LanguageDirection) -> Unit,
    onSourceSelected: (SupportedLanguage?) -> Unit,
    onTargetSelected: (SupportedLanguage) -> Unit,
    onTargetChange: ((LanguagePickerTarget) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onTargetChange != null) {
            LanguageToggleRow(
                target = target,
                onTargetChange = onTargetChange
            )
        }
        Text(
            text = when (target) {
                LanguagePickerTarget.Source -> stringResource(id = R.string.settings_source_language_label)
                LanguagePickerTarget.Target -> stringResource(id = R.string.settings_target_language_label)
            },
            style = MaterialTheme.typography.titleMedium
        )
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = stringResource(id = R.string.settings_language_search_hint)) },
            singleLine = true
        )
        if (favorites.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_language_favorites_section),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowFavoriteChips(
                    favorites = favorites,
                    onFavoriteSelected = onFavoriteSelected
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (target == LanguagePickerTarget.Source) {
                item("auto-detect") {
                    LanguageOptionRow(
                        title = stringResource(id = R.string.settings_auto_detect_label),
                        subtitle = null,
                        selected = isAutoDetectEnabled,
                        onClick = { onSourceSelected(null) }
                    )
                }
            }
            items(languages, key = { it.code }) { language ->
                val selected = when (target) {
                    LanguagePickerTarget.Source -> currentDirection.sourceLanguage == language && !isAutoDetectEnabled
                    LanguagePickerTarget.Target -> currentDirection.targetLanguage == language
                }
                LanguageOptionRow(
                    title = language.displayName,
                    subtitle = language.code,
                    selected = selected,
                    onClick = {
                        when (target) {
                            LanguagePickerTarget.Source -> onSourceSelected(language)
                            LanguagePickerTarget.Target -> onTargetSelected(language)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowFavoriteChips(
    favorites: List<LanguageDirection>,
    onFavoriteSelected: (LanguageDirection) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        favorites.forEach { direction ->
            val label = directionLabel(direction)
            AssistChip(
                onClick = { onFavoriteSelected(direction) },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun LanguageToggleRow(
    target: LanguagePickerTarget,
    onTargetChange: (LanguagePickerTarget) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LanguageToggleButton(
            label = stringResource(id = R.string.settings_language_tab_source),
            selected = target == LanguagePickerTarget.Source,
            modifier = Modifier.weight(1f),
            onClick = { onTargetChange(LanguagePickerTarget.Source) }
        )
        LanguageToggleButton(
            label = stringResource(id = R.string.settings_language_tab_target),
            selected = target == LanguagePickerTarget.Target,
            modifier = Modifier.weight(1f),
            onClick = { onTargetChange(LanguagePickerTarget.Target) }
        )
    }
}

@Composable
private fun LanguageToggleButton(
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
private fun LanguageOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let { sub ->
            { Text(text = sub, style = MaterialTheme.typography.bodySmall) }
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    )
}

private fun directionLabel(direction: LanguageDirection): String {
    val source = direction.sourceLanguage?.displayName ?: "自动检测"
    val target = direction.targetLanguage.displayName
    return "$source → $target"
}
