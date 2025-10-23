package com.example.translatorapp.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.ThemeMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.UserSettings
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val direction = stringPreferencesKey("direction")
        val modelProfile = stringPreferencesKey("model_profile")
        val telemetry = booleanPreferencesKey("telemetry")
        val sourceLanguage = stringPreferencesKey("source_language")
        val targetLanguage = stringPreferencesKey("target_language")
        val autoDetect = booleanPreferencesKey("auto_detect_source")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val accountId = stringPreferencesKey("account_id")
        val accountEmail = stringPreferencesKey("account_email")
        val accountDisplayName = stringPreferencesKey("account_display_name")
        val lastSyncedAt = stringPreferencesKey("last_synced_at")
        val apiHost = stringPreferencesKey("api_host")
        val themeMode = stringPreferencesKey("theme_mode")
        val appLanguage = stringPreferencesKey("app_language")
    }

    val settings: Flow<UserSettings> = context.userPrefsDataStore.data.map { prefs ->
        val legacyDirection = prefs[Keys.direction]?.let { LanguageDirection.decode(it) }
        val autoDetect = prefs[Keys.autoDetect] ?: legacyDirection?.isAutoDetect ?: false
        val sourceLanguage = prefs[Keys.sourceLanguage]?.let { SupportedLanguage.fromCode(it) }
            ?: legacyDirection?.sourceLanguage
        val targetLanguage = prefs[Keys.targetLanguage]?.let { SupportedLanguage.fromCode(it) }
            ?: legacyDirection?.targetLanguage
            ?: UserSettings().direction.targetLanguage
        val direction = if (autoDetect) {
            LanguageDirection(null, targetLanguage)
        } else {
            LanguageDirection(sourceLanguage ?: UserSettings().direction.sourceLanguage, targetLanguage)
        }
        UserSettings(
            direction = direction,
            translationProfile = prefs[Keys.modelProfile]?.let {
                runCatching { TranslationModelProfile.valueOf(it) }.getOrNull()
            } ?: UserSettings().translationProfile,
            themeMode = prefs[Keys.themeMode]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: UserSettings().themeMode,
            appLanguage = prefs[Keys.appLanguage]?.let { stored ->
                when (stored) {
                    "en", "en_US", "en-US" -> "en-US"
                    "zh", "zh_CN", "zh-CN" -> "zh-CN"
                    "es", "es_ES", "es-ES" -> "es-ES"
                    "fr", "fr_FR", "fr-FR" -> "fr-FR"
                    else -> stored
                }
            },
            allowTelemetry = prefs[Keys.telemetry] ?: UserSettings().allowTelemetry,
            syncEnabled = prefs[Keys.syncEnabled] ?: UserSettings().syncEnabled,
            accountId = prefs[Keys.accountId],
            accountEmail = prefs[Keys.accountEmail],
            accountDisplayName = prefs[Keys.accountDisplayName],
            lastSyncedAt = prefs[Keys.lastSyncedAt]?.let { runCatching { Instant.parse(it) }.getOrNull() },
            apiEndpoint = prefs[Keys.apiHost] ?: ""
        )
    }

    suspend fun update(settings: UserSettings) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.direction] = settings.direction.encode()
            if (settings.direction.sourceLanguage == null) {
                prefs.remove(Keys.sourceLanguage)
                prefs[Keys.autoDetect] = true
            } else {
                prefs[Keys.sourceLanguage] = settings.direction.sourceLanguage.code
                prefs[Keys.autoDetect] = false
            }
            prefs[Keys.targetLanguage] = settings.direction.targetLanguage.code
            prefs[Keys.modelProfile] = settings.translationProfile.name
            prefs[Keys.themeMode] = settings.themeMode.name
            prefs[Keys.telemetry] = settings.allowTelemetry
            prefs[Keys.syncEnabled] = settings.syncEnabled
            if (settings.accountId.isNullOrBlank()) {
                prefs.remove(Keys.accountId)
            } else {
                prefs[Keys.accountId] = settings.accountId
            }
            if (settings.accountEmail.isNullOrBlank()) {
                prefs.remove(Keys.accountEmail)
            } else {
                prefs[Keys.accountEmail] = settings.accountEmail
            }
            if (settings.accountDisplayName.isNullOrBlank()) {
                prefs.remove(Keys.accountDisplayName)
            } else {
                prefs[Keys.accountDisplayName] = settings.accountDisplayName
            }
            settings.lastSyncedAt?.let {
                prefs[Keys.lastSyncedAt] = it.toString()
            } ?: prefs.remove(Keys.lastSyncedAt)
            if (settings.apiEndpoint.isBlank()) {
                prefs.remove(Keys.apiHost)
            } else {
                prefs[Keys.apiHost] = settings.apiEndpoint
            }
            settings.appLanguage
                ?.takeIf { it.isNotBlank() }
                ?.let { tag ->
                    val normalized = when (tag) {
                        "en" -> "en-US"
                        "zh" -> "zh-CN"
                        "es" -> "es-ES"
                        "fr" -> "fr-FR"
                        else -> tag
                    }
                    prefs[Keys.appLanguage] = normalized
                } ?: prefs.remove(Keys.appLanguage)
        }
    }
}
