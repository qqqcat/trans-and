package com.example.translatorapp.presentation.history

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.presentation.theme.LocalSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    paddingValues: PaddingValues
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val shareItem: (TranslationHistoryItem) -> Unit = { item ->
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
    }

    HistoryScreen(
        state = state,
        paddingValues = paddingValues,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleFavoritesOnly = viewModel::toggleFavoriteFilter,
        onTagFilterToggle = viewModel::onTagFilterToggle,
        onClearHistory = viewModel::clearHistory,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        onTagDraftChange = viewModel::onTagDraftChange,
        onTagDraftCommit = viewModel::onTagDraftCommit,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onShareItem = shareItem
    )
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    paddingValues: PaddingValues,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onTagFilterToggle: (String) -> Unit,
    onClearHistory: () -> Unit,
    onFavoriteToggle: (Long, Boolean) -> Unit,
    onTagDraftChange: (Long, String) -> Unit,
    onTagDraftCommit: (Long) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onShareItem: (TranslationHistoryItem) -> Unit
) {
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh
    )
    val bottomPadding = paddingValues.calculateBottomPadding()
    val topPadding = paddingValues.calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            stickyHeader {
                HistoryFiltersHeader(
                    state = state,
                    onSearchQueryChange = onSearchQueryChange,
                    onToggleFavoritesOnly = onToggleFavoritesOnly,
                    onTagFilterToggle = onTagFilterToggle,
                    onClearHistory = onClearHistory
                )
            }

            if (state.visibleHistory.isEmpty()) {
                item {
                    HistoryEmptyState(modifier = Modifier.fillMaxWidth())
                }
            } else {
                items(state.visibleHistory, key = { it.id }) { item ->
                    HistoryEntryCard(
                        item = item,
                        tagDraft = state.tagDrafts[item.id] ?: "",
                        onFavoriteToggle = onFavoriteToggle,
                        onTagDraftChange = onTagDraftChange,
                        onTagDraftCommit = onTagDraftCommit,
                        onShareItem = onShareItem
                    )
                }
            }

            if (state.canLoadMore) {
                item {
                    HistoryLoadMoreFooter(
                        isLoading = state.isLoadingMore,
                        onLoadMore = onLoadMore
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = state.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryFiltersHeader(
    state: HistoryUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onTagFilterToggle: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.md)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
                label = { Text(text = stringResource(id = R.string.history_search_placeholder)) },
                singleLine = true,
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null)
                        }
                    }
                }
            )
            TextButton(onClick = onClearHistory) {
                Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(spacing.xs))
                Text(text = stringResource(id = R.string.history_clear_all))
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            FilterChip(
                selected = state.showFavoritesOnly,
                onClick = onToggleFavoritesOnly,
                label = { Text(text = stringResource(id = R.string.history_filter_favorites)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (state.showFavoritesOnly) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null
                    )
                }
            )
            state.availableTags.forEach { tag ->
                FilterChip(
                    selected = tag in state.selectedTags,
                    onClick = { onTagFilterToggle(tag) },
                    label = { Text(text = tag) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryEntryCard(
    item: TranslationHistoryItem,
    tagDraft: String,
    onFavoriteToggle: (Long, Boolean) -> Unit,
    onTagDraftChange: (Long, String) -> Unit,
    onTagDraftCommit: (Long) -> Unit,
    onShareItem: (TranslationHistoryItem) -> Unit
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = formatTimestamp(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = directionLabel(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onFavoriteToggle(item.id, !item.isFavorite) }) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = item.sourceText,
                style = MaterialTheme.typography.titleMedium
            )
            Divider()
            Text(
                text = item.translatedText,
                style = MaterialTheme.typography.bodyMedium
            )

            if (item.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    item.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(text = tag) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = tagDraft,
                onValueChange = { onTagDraftChange(item.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(id = R.string.history_tag_input_label)) },
                placeholder = { Text(text = stringResource(id = R.string.history_tag_input_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onTagDraftCommit(item.id) }) {
                        Icon(imageVector = Icons.Outlined.FilterList, contentDescription = null)
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onShareItem(item) }) {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(text = stringResource(id = R.string.history_share_action))
                }
            }
        }
    }
}

@Composable
private fun HistoryLoadMoreFooter(
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator()
        } else {
            OutlinedButton(onClick = onLoadMore) {
                Text(text = stringResource(id = R.string.history_load_more))
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(
            text = stringResource(id = R.string.history_empty_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun formatTimestamp(item: TranslationHistoryItem): String {
    val localTime = item.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%04d-%02d-%02d %02d:%02d".format(
        localTime.year,
        localTime.monthNumber,
        localTime.dayOfMonth,
        localTime.hour,
        localTime.minute
    )
}

private fun directionLabel(item: TranslationHistoryItem): String {
    val source = item.direction.sourceLanguage?.displayName ?: "自动检测"
    val target = item.direction.targetLanguage.displayName
    return "$source → $target"
}
