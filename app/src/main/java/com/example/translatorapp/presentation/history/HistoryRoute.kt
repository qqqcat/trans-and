package com.example.translatorapp.presentation.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.TranslationHistoryItem
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    paddingValues: PaddingValues,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    HistoryScreen(
        state = uiState,
        paddingValues = paddingValues,
        onBack = onBack,
        onClear = viewModel::clearHistory,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onShareItem = { item ->
            val shareText = context.getString(
                R.string.history_share_content,
                item.sourceText,
                item.translatedText
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.history_share_title)))
        },
        onToggleFavoriteFilter = viewModel::toggleFavoriteFilter,
        onTagFilterToggle = viewModel::onTagFilterToggle,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        onTagDraftChange = viewModel::onTagDraftChange,
        onTagDraftCommit = viewModel::onTagDraftCommit
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onShareItem: (TranslationHistoryItem) -> Unit,
    onToggleFavoriteFilter: () -> Unit,
    onTagFilterToggle: (String) -> Unit,
    onFavoriteToggle: (Long, Boolean) -> Unit,
    onTagDraftChange: (Long, String) -> Unit,
    onTagDraftCommit: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            placeholder = { Text(text = stringResource(id = R.string.history_search_placeholder)) }
        )
        androidx.compose.material3.FilterChip(
            selected = state.showFavoritesOnly,
            onClick = onToggleFavoriteFilter,
            label = { Text(text = stringResource(id = R.string.history_filter_favorites)) },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Bookmark, contentDescription = null)
            }
        )
        if (state.availableTags.isNotEmpty()) {
            Text(
                text = stringResource(id = R.string.history_filter_tags_label),
                style = MaterialTheme.typography.labelMedium
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableTags.forEach { tag ->
                    androidx.compose.material3.FilterChip(
                        selected = tag in state.selectedTags,
                        onClick = { onTagFilterToggle(tag) },
                        label = { Text(text = "#$tag") }
                    )
                }
            }
        }
        if (state.filteredHistory.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.history_empty_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.filteredHistory) { item ->
                    HistoryListItem(
                        item = item,
                        tagDraft = state.tagDrafts[item.id].orEmpty(),
                        onShare = { onShareItem(item) },
                        onFavoriteToggle = { onFavoriteToggle(item.id, !item.isFavorite) },
                        onTagDraftChange = { onTagDraftChange(item.id, it) },
                        onTagDraftCommit = { onTagDraftCommit(item.id) }
                    )
                    Divider()
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = R.string.history_clear_all))
            }
            Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = R.string.history_back))
            }
        }
    }
}

@Composable
private fun HistoryListItem(
    item: TranslationHistoryItem,
    tagDraft: String,
    onShare: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onTagDraftChange: (String) -> Unit,
    onTagDraftCommit: () -> Unit,
) {
    val createdAt = item.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    id = R.string.history_timestamp_label,
                    createdAt.date.toString(),
                    "%02d:%02d:%02d".format(createdAt.hour, createdAt.minute, createdAt.second)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconToggleButton(
                    checked = item.isFavorite,
                    onCheckedChange = { onFavoriteToggle() }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = stringResource(id = R.string.history_favorite_action)
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(id = R.string.history_share_action)
                    )
                }
            }
        }
        Text(
            text = stringResource(id = R.string.subtitle_timeline_source_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = item.sourceText,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(id = R.string.subtitle_timeline_translation_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = item.translatedText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (item.tags.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.tags.forEach { tag ->
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        label = { Text(text = "#$tag") }
                    )
                }
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = tagDraft,
            onValueChange = onTagDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(id = R.string.history_tag_input_label)) },
            trailingIcon = {
                IconButton(onClick = onTagDraftCommit) {
                    Icon(
                        imageVector = Icons.Outlined.Done,
                        contentDescription = stringResource(id = R.string.history_save_tags_action)
                    )
                }
            },
            placeholder = { Text(text = stringResource(id = R.string.history_tag_input_hint)) }
        )
    }
}
