package com.example.translatorapp.domain.usecase

import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.AccountSyncStatus
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.ThemeMode
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.repository.TranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRepository : TranslationRepository {
    val startCalls = MutableStateFlow<UserSettings?>(null)
    val toggleCalls = MutableStateFlow<Boolean?>(null)
    val directionCalls = MutableStateFlow<LanguageDirection?>(null)
    val modelCalls = MutableStateFlow<TranslationModelProfile?>(null)
    val telemetryCalls = MutableStateFlow<Boolean?>(null)

    override val sessionState: Flow<TranslationSessionState> = MutableStateFlow(TranslationSessionState())
    override val liveTranscription: Flow<TranslationContent> = MutableSharedFlow()
    override val history: Flow<List<TranslationHistoryItem>> = MutableStateFlow(emptyList())
    override val settings: Flow<UserSettings> = MutableStateFlow(UserSettings())

    override suspend fun startRealtimeSession(settings: UserSettings) {
        startCalls.value = settings
    }

    override suspend fun stopRealtimeSession() {}

    override suspend fun toggleMicrophone(): Boolean {
        toggleCalls.value = !(toggleCalls.value ?: false)
        return toggleCalls.value ?: false
    }

    override suspend fun updateDirection(direction: LanguageDirection) {
        directionCalls.value = direction
    }

    override suspend fun updateModel(profile: TranslationModelProfile) {
        modelCalls.value = profile
    }

    override suspend fun updateTelemetryConsent(consent: Boolean) {
        telemetryCalls.value = consent
    }

    override suspend fun persistHistoryItem(content: TranslationContent) {}

    override suspend fun clearHistory() {}

    override suspend fun refreshSettings(): UserSettings = UserSettings()

    override suspend fun translateText(
        text: String,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent = TranslationContent(
        transcript = text,
        translation = "translated"
    )

    override suspend fun translateImage(
        imageBytes: ByteArray,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent = TranslationContent(
        transcript = "image",
        translation = "translated"
    )

    override suspend fun detectLanguage(text: String): SupportedLanguage? = null

    override suspend fun updateHistoryFavorite(id: Long, isFavorite: Boolean) {}

    override suspend fun updateHistoryTags(id: Long, tags: Set<String>) {}

    override suspend fun syncAccount(): AccountSyncStatus = AccountSyncStatus(success = true)

    override suspend fun updateAccountProfile(profile: AccountProfile) {}

    override suspend fun updateSyncEnabled(enabled: Boolean) {}

    override suspend fun updateApiEndpoint(endpoint: String) {}

    override suspend fun updateThemeMode(themeMode: ThemeMode) {}

    override suspend fun updateAppLanguage(language: String?) {}
}


class StartRealtimeSessionUseCaseTest {

    @Test
    fun `starting session forwards settings`() = runTest {
        val fakeRepository = FakeRepository()
        val useCase = StartRealtimeSessionUseCase(fakeRepository)
        val settings = UserSettings(
            direction = LanguageDirection(
                sourceLanguage = SupportedLanguage.ChineseSimplified,
                targetLanguage = SupportedLanguage.English
            )
        )

        useCase(settings)

        assertEquals(settings, fakeRepository.startCalls.value)
    }

    @Test
    fun `toggle microphone delegates to repository`() = runTest {
        val fakeRepository = FakeRepository()
        val useCase = ToggleMicrophoneUseCase(fakeRepository)

        val result = useCase()

        assertTrue(result)
        assertEquals(true, fakeRepository.toggleCalls.value)
    }
}
