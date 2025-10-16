package com.example.translatorapp.domain.usecase

import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
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
    val offlineCalls = MutableStateFlow<Boolean?>(null)
    val telemetryCalls = MutableStateFlow<Boolean?>(null)

    override val sessionState: Flow<TranslationSessionState> = MutableStateFlow(TranslationSessionState())
    override val liveTranscription: Flow<TranslationContent> = MutableSharedFlow()
    override val history: Flow<List<TranslationHistoryItem>> = MutableStateFlow(emptyList())

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

    override suspend fun updateOfflineFallback(enabled: Boolean) {
        offlineCalls.value = enabled
    }

    override suspend fun updateTelemetryConsent(consent: Boolean) {
        telemetryCalls.value = consent
    }

    override suspend fun persistHistoryItem(content: TranslationContent) {}

    override suspend fun clearHistory() {}

    override suspend fun refreshSettings(): UserSettings = UserSettings()
}

class StartRealtimeSessionUseCaseTest {

    @Test
    fun `starting session forwards settings`() = runTest {
        val fakeRepository = FakeRepository()
        val useCase = StartRealtimeSessionUseCase(fakeRepository)
        val settings = UserSettings(direction = LanguageDirection.FrenchToChinese)

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

    @Test
    fun `update offline fallback persists flag`() = runTest {
        val fakeRepository = FakeRepository()
        val useCase = UpdateOfflineFallbackUseCase(fakeRepository)

        useCase(true)

        assertEquals(true, fakeRepository.offlineCalls.value)
    }
}
