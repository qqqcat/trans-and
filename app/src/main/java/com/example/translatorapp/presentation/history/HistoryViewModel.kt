package com.example.translatorapp.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.usecase.ClearHistoryUseCase
import com.example.translatorapp.domain.usecase.ObserveHistoryUseCase
import com.example.translatorapp.domain.usecase.UpdateHistoryFavoriteUseCase
import com.example.translatorapp.domain.usecase.UpdateHistoryTagsUseCase
import com.example.translatorapp.util.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistoryUseCase: ObserveHistoryUseCase,
    private val clearHistoryUseCase: ClearHistoryUseCase,
    private val updateHistoryFavoriteUseCase: UpdateHistoryFavoriteUseCase,
    private val updateHistoryTagsUseCase: UpdateHistoryTagsUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            observeHistoryUseCase().collect { items ->
                _uiState.update { current ->
                    val sanitizedVisibleCount = if (items.isEmpty()) {
                        HISTORY_PAGE_SIZE
                    } else {
                        current.visibleCount.coerceAtMost(items.size).coerceAtLeast(HISTORY_PAGE_SIZE)
                    }
                    current.copy(
                        history = items,
                        tagDrafts = items.associate { item ->
                            item.id to item.tags.joinToString(separator = ", ")
                        },
                        visibleCount = sanitizedVisibleCount,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, visibleCount = HISTORY_PAGE_SIZE) }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            var shouldReset = false
            _uiState.update { state ->
                if (!state.canLoadMore) {
                    state
                } else {
                    shouldReset = true
                    state.copy(
                        isLoadingMore = true,
                        visibleCount = state.visibleCount + HISTORY_PAGE_SIZE
                    )
                }
            }
            if (shouldReset) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(dispatcherProvider.io) {
            clearHistoryUseCase()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(query = query, visibleCount = HISTORY_PAGE_SIZE) }
    }

    fun toggleFavoriteFilter() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly, visibleCount = HISTORY_PAGE_SIZE) }
    }

    fun onTagFilterToggle(tag: String) {
        _uiState.update { state ->
            val newSelection = state.selectedTags.toMutableSet().apply {
                if (!add(tag)) remove(tag)
            }
            state.copy(selectedTags = newSelection, visibleCount = HISTORY_PAGE_SIZE)
        }
    }

    fun onFavoriteToggle(id: Long, isFavorite: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateHistoryFavoriteUseCase(id, isFavorite)
        }
    }

    fun onTagDraftChange(id: Long, value: String) {
        _uiState.update { it.copy(tagDrafts = it.tagDrafts + (id to value)) }
    }

    fun onTagDraftCommit(id: Long) {
        val draft = _uiState.value.tagDrafts[id] ?: return
        val tags = draft.split(',', ';', '；', ' ').mapNotNull { token ->
            val trimmed = token.trim()
            trimmed.takeIf { it.isNotEmpty() }
        }.toSet()
        viewModelScope.launch(dispatcherProvider.io) {
            updateHistoryTagsUseCase(id, tags)
        }
    }
}
