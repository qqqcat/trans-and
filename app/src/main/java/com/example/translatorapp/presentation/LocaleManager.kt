package com.example.translatorapp.presentation

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleManager {
    fun applyLocale(languageTag: String?) {
        val tag = languageTag
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizeTag(it) }
        val locales = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() == locales.toLanguageTags()) {
            return
        }
        if (locales.size() > 0) {
            locales[0]?.let { Locale.setDefault(it) }
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun normalizeTag(tag: String): String = when (tag) {
        "en", "en_US", "en-US" -> "en-US"
        "zh", "zh_CN", "zh-CN" -> "zh-CN"
        "zh_TW", "zh-TW" -> "zh-TW"
        "es", "es_ES", "es-ES" -> "es-ES"
        "fr", "fr_FR", "fr-FR" -> "fr-FR"
        "ja", "ja_JP", "ja-JP" -> "ja-JP"
        "ko", "ko_KR", "ko-KR" -> "ko-KR"
        "ar", "ar_SA", "ar-SA" -> "ar-SA"
        "ru", "ru_RU", "ru-RU" -> "ru-RU"
        else -> tag
    }
}
