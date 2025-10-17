package com.example.translatorapp.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.translatorapp.domain.model.LanguageCatalog
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.UserSettings
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
        val offlineFallback = booleanPreferencesKey("offline_fallback")
        val telemetry = booleanPreferencesKey("telemetry")
    }

    val settings: Flow<UserSettings> = context.userPrefsDataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            direction = prefs[Keys.direction]?.let(LanguageCatalog::findDirection) ?: defaults.direction,
            translationProfile = prefs[Keys.modelProfile]?.let { runCatching { TranslationModelProfile.valueOf(it) }.getOrNull() }
                ?: defaults.translationProfile,
            offlineFallbackEnabled = prefs[Keys.offlineFallback] ?: defaults.offlineFallbackEnabled,
            allowTelemetry = prefs[Keys.telemetry] ?: defaults.allowTelemetry
        )
    }

    suspend fun update(settings: UserSettings) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.direction] = settings.direction.id
            prefs[Keys.modelProfile] = settings.translationProfile.name
            prefs[Keys.offlineFallback] = settings.offlineFallbackEnabled
            prefs[Keys.telemetry] = settings.allowTelemetry
        }
    }
}
