package com.example.translatorapp.presentation.history

import com.example.translatorapp.domain.model.TranslationHistoryItem

data class HistoryUiState(
    val query: String = "",
    val history: List<TranslationHistoryItem> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val selectedTags: Set<String> = emptySet(),
    val tagDrafts: Map<Long, String> = emptyMap(),
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

    val availableTags: Set<String>
        get() = history.flatMap { it.tags }.toSortedSet()
}
