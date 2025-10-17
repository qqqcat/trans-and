package com.example.translatorapp.data.model

import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationInputMode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationHistoryEntityTest {

    @Test
    fun toEntity_andBack_preservesCoreFields() {
        val timestamp = Instant.parse("2024-01-01T08:00:00Z")
        val item = TranslationHistoryItem(
            id = 42,
            direction = LanguageDirection(
                sourceLanguage = SupportedLanguage.English,
                targetLanguage = SupportedLanguage.ChineseSimplified
            ),
            sourceText = "Hello",
            translatedText = "你好",
            createdAt = timestamp,
            inputMode = TranslationInputMode.Text,
            detectedSourceLanguage = SupportedLanguage.English,
            isFavorite = true,
            tags = setOf("greeting", "demo")
        )

        val entity = item.toEntity()
        val roundTrip = entity.toDomain()

        assertEquals(item.id, entity.id)
        assertEquals(item.direction, roundTrip.direction)
        assertEquals(item.sourceText, roundTrip.sourceText)
        assertEquals(item.translatedText, roundTrip.translatedText)
        assertEquals(item.inputMode, roundTrip.inputMode)
        assertEquals(item.tags, roundTrip.tags)
        assertTrue(roundTrip.isFavorite)
    }

    @Test
    fun toDomain_withoutExplicitLanguages_usesEncodedDirection() {
        val now = Clock.System.now()
        val entity = TranslationHistoryEntity(
            id = 1,
            direction = LanguageDirection(
                sourceLanguage = null,
                targetLanguage = SupportedLanguage.Japanese
            ).encode(),
            sourceText = "test",
            translatedText = "テスト",
            createdAt = now.toString(),
            sourceLanguage = null,
            targetLanguage = null,
            detectedLanguage = null,
            inputMode = TranslationInputMode.Voice.name,
            isFavorite = false,
            tags = ""
        )

        val domain = entity.toDomain()

        assertTrue(domain.direction.isAutoDetect)
        assertEquals(SupportedLanguage.Japanese, domain.direction.targetLanguage)
    }
}
