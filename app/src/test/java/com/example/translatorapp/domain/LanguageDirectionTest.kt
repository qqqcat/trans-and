package com.example.translatorapp.domain

import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDirectionTest {

    @Test
    fun encodeAndDecode_roundTripsManualLanguages() {
        val direction = LanguageDirection(
            sourceLanguage = SupportedLanguage.French,
            targetLanguage = SupportedLanguage.English
        )

        val encoded = direction.encode()
        val decoded = LanguageDirection.decode(encoded)

        assertEquals(direction, decoded)
    }

    @Test
    fun decode_autoDetectSourceRestoresNullSourceLanguage() {
        val encoded = LanguageDirection(
            sourceLanguage = null,
            targetLanguage = SupportedLanguage.Spanish
        ).encode()

        val decoded = LanguageDirection.decode(encoded)

        assertNull(decoded.sourceLanguage)
        assertTrue(decoded.isAutoDetect)
        assertEquals(SupportedLanguage.Spanish, decoded.targetLanguage)
    }

    @Test
    fun decode_legacyIdentifierStillProducesExpectedDirection() {
        val decoded = LanguageDirection.decode("ChineseToFrench")

        assertEquals(SupportedLanguage.ChineseSimplified, decoded.sourceLanguage)
        assertEquals(SupportedLanguage.French, decoded.targetLanguage)
    }
}
