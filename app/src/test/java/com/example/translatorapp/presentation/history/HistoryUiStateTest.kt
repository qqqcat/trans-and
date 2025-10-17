package com.example.translatorapp.presentation.history

import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationInputMode
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryUiStateTest {

    @Test
    fun visibleHistoryHonorsVisibleCount() {
        val items = (0 until 30).map { sampleHistoryItem(it.toLong(), "source$it", "target$it") }
        val state = HistoryUiState(history = items, visibleCount = 12)

        assertEquals(12, state.visibleHistory.size)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun visibleHistoryRespectsFilters() {
        val items = listOf(
            sampleHistoryItem(1, source = "hello", target = "world", tags = setOf("work")),
            sampleHistoryItem(2, source = "bonjour", target = "monde", tags = setOf("travel")),
            sampleHistoryItem(3, source = "hola mon", target = "monumento", tags = setOf("travel", "work"))
        )
        val state = HistoryUiState(
            history = items,
            query = "mon",
            showFavoritesOnly = false,
            selectedTags = setOf("travel"),
            visibleCount = 5
        )

        val filtered = state.visibleHistory
        assertEquals(2, filtered.size)
        assertFalse(state.copy(visibleCount = 10).canLoadMore)
    }

    private fun sampleHistoryItem(
        id: Long,
        source: String,
        target: String,
        tags: Set<String> = emptySet(),
        favorite: Boolean = false
    ): TranslationHistoryItem = TranslationHistoryItem(
        id = id,
        direction = LanguageDirection(
            sourceLanguage = SupportedLanguage.English,
            targetLanguage = SupportedLanguage.ChineseSimplified
        ),
        sourceText = source,
        translatedText = target,
        createdAt = Clock.System.now(),
        inputMode = TranslationInputMode.Text,
        detectedSourceLanguage = SupportedLanguage.English,
        isFavorite = favorite,
        tags = tags
    )
}
