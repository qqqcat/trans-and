package com.example.translatorapp.presentation.history

import com.example.translatorapp.domain.model.TranslationHistoryItem

internal const val HISTORY_PAGE_SIZE = 20

data class HistoryUiState(
    val query: String = "",
    val history: List<TranslationHistoryItem> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val selectedTags: Set<String> = emptySet(),
    val tagDrafts: Map<Long, String> = emptyMap(),
    val visibleCount: Int = HISTORY_PAGE_SIZE,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false
) {
    val filteredHistory: List<TranslationHistoryItem>
        get() = history.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.sourceText.contains(query, ignoreCase = true) ||
                item.translatedText.contains(query, ignoreCase = true)
            val matchesFavorite = !showFavoritesOnly || item.isFavorite
            val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in item.tags }
            matchesQuery && matchesFavorite && matchesTags
        }

    val visibleHistory: List<TranslationHistoryItem>
        get() = filteredHistory.take(visibleCount)

    val availableTags: Set<String>
        get() = history.flatMap { it.tags }.toSortedSet()

    val canLoadMore: Boolean
        get() = visibleHistory.size < filteredHistory.size
}
