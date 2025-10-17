package com.example.translatorapp.presentation.history

import com.example.translatorapp.domain.model.TranslationHistoryItem

data class HistoryUiState(
    val query: String = "",
    val history: List<TranslationHistoryItem> = emptyList(),
) {
    val filteredHistory: List<TranslationHistoryItem>
        get() = if (query.isBlank()) {
            history
        } else {
            history.filter {
                it.sourceText.contains(query, ignoreCase = true) ||
                    it.translatedText.contains(query, ignoreCase = true)
            }
        }
}
