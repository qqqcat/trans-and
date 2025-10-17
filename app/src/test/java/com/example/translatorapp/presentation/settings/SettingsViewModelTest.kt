package com.example.translatorapp.presentation.settings

import com.example.translatorapp.domain.model.AccountProfile
import com.example.translatorapp.domain.model.AccountSyncStatus
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationHistoryItem
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.repository.TranslationRepository
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.SyncAccountUseCase
import com.example.translatorapp.domain.usecase.UpdateAccountProfileUseCase
import com.example.translatorapp.domain.usecase.UpdateDirectionUseCase
import com.example.translatorapp.domain.usecase.UpdateModelUseCase
import com.example.translatorapp.domain.usecase.UpdateOfflineFallbackUseCase
import com.example.translatorapp.domain.usecase.UpdateTelemetryConsentUseCase
import com.example.translatorapp.domain.usecase.UpdateSyncEnabledUseCase
import com.example.translatorapp.util.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    @Test
    fun onDirectionSelected_updatesRepositoryAndUiState() = runTest {
        val repository = FakeTranslationRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val newDirection = LanguageDirection(
            sourceLanguage = SupportedLanguage.English,
            targetLanguage = SupportedLanguage.Japanese
        )

        viewModel.onDirectionSelected(newDirection)
        advanceUntilIdle()

        assertEquals(newDirection, repository.currentSettings.direction)
        assertEquals(newDirection, viewModel.uiState.value.settings.direction)
    }

    @Test
    fun onAutoDetectChanged_updatesDirectionAndFlags() = runTest {
        val repository = FakeTranslationRepository(
            UserSettings(
                direction = LanguageDirection(
                    sourceLanguage = SupportedLanguage.ChineseSimplified,
                    targetLanguage = SupportedLanguage.English
                )
            )
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onAutoDetectChanged(true)
        advanceUntilIdle()

        assertNull(repository.currentSettings.direction.sourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetectEnabled)

        viewModel.onSourceLanguageSelected(SupportedLanguage.French)
        viewModel.onAutoDetectChanged(false)
        advanceUntilIdle()

        assertEquals(SupportedLanguage.French, repository.currentSettings.direction.sourceLanguage)
        assertFalse(viewModel.uiState.value.isAutoDetectEnabled)
    }

    @Test
    fun onSaveAccount_persistsAccountDetails() = runTest {
        val repository = FakeTranslationRepository(
            UserSettings(
                accountId = null,
                accountEmail = "",
                accountDisplayName = ""
            )
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onAccountEmailChange("user@example.com")
        viewModel.onAccountDisplayNameChange("Test User")
        viewModel.onSaveAccount()
        advanceUntilIdle()

        assertEquals("user@example.com", repository.currentSettings.accountEmail)
        assertEquals("Test User", repository.currentSettings.accountDisplayName)
        assertEquals("user@example.com", viewModel.uiState.value.accountEmail)
        assertEquals("Test User", viewModel.uiState.value.accountDisplayName)
    }

    @Test
    fun onSyncNow_invokesRepositoryAndUpdatesUi() = runTest {
        val syncedAt = Instant.parse("2024-06-01T12:30:00Z")
        val repository = FakeTranslationRepository()
        repository.nextSyncStatus = AccountSyncStatus(
            success = true,
            message = "synced",
            syncedAt = syncedAt
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onSyncNow()
        advanceUntilIdle()

        assertEquals(1, repository.syncCallCount)
        assertEquals("synced", viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.lastSyncDisplay?.contains("2024-06-01") == true)
    }

    private fun createViewModel(repository: FakeTranslationRepository): SettingsViewModel {
        return SettingsViewModel(
            loadSettingsUseCase = LoadSettingsUseCase(repository),
            updateDirectionUseCase = UpdateDirectionUseCase(repository),
            updateModelUseCase = UpdateModelUseCase(repository),
            updateOfflineFallbackUseCase = UpdateOfflineFallbackUseCase(repository),
            updateTelemetryConsentUseCase = UpdateTelemetryConsentUseCase(repository),
            updateAccountProfileUseCase = UpdateAccountProfileUseCase(repository),
            updateSyncEnabledUseCase = UpdateSyncEnabledUseCase(repository),
            syncAccountUseCase = SyncAccountUseCase(repository),
            dispatcherProvider = dispatcherProvider
        )
    }
}

private class FakeTranslationRepository(
    initialSettings: UserSettings = UserSettings()
) : TranslationRepository {

    override val sessionState: Flow<TranslationSessionState> = MutableStateFlow(TranslationSessionState())
    override val liveTranscription: Flow<TranslationContent> = MutableSharedFlow()
    override val history: Flow<List<TranslationHistoryItem>> = MutableStateFlow(emptyList())

    var currentSettings: UserSettings = initialSettings
        private set
    var nextSyncStatus: AccountSyncStatus = AccountSyncStatus(success = true, message = null, syncedAt = null)
    var syncCallCount: Int = 0

    override suspend fun startRealtimeSession(settings: UserSettings) = Unit

    override suspend fun stopRealtimeSession() = Unit

    override suspend fun toggleMicrophone(): Boolean = false

    override suspend fun updateDirection(direction: LanguageDirection) {
        currentSettings = currentSettings.copy(direction = direction)
    }

    override suspend fun updateModel(profile: TranslationModelProfile) {
        currentSettings = currentSettings.copy(translationProfile = profile)
    }

    override suspend fun updateOfflineFallback(enabled: Boolean) {
        currentSettings = currentSettings.copy(offlineFallbackEnabled = enabled)
    }

    override suspend fun updateTelemetryConsent(consent: Boolean) {
        currentSettings = currentSettings.copy(allowTelemetry = consent)
    }

    override suspend fun persistHistoryItem(content: TranslationContent) = Unit

    override suspend fun clearHistory() = Unit

    override suspend fun refreshSettings(): UserSettings = currentSettings

    override suspend fun translateText(
        text: String,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent {
        throw UnsupportedOperationException("Not required in this test double.")
    }

    override suspend fun translateImage(
        imageBytes: ByteArray,
        direction: LanguageDirection,
        profile: TranslationModelProfile
    ): TranslationContent {
        throw UnsupportedOperationException("Not required in this test double.")
    }

    override suspend fun detectLanguage(text: String): SupportedLanguage? = null

    override suspend fun updateHistoryFavorite(id: Long, isFavorite: Boolean) = Unit

    override suspend fun updateHistoryTags(id: Long, tags: Set<String>) = Unit

    override suspend fun syncAccount(): AccountSyncStatus {
        syncCallCount += 1
        nextSyncStatus.syncedAt?.let { instant ->
            currentSettings = currentSettings.copy(lastSyncedAt = instant)
        }
        return nextSyncStatus
    }

    override suspend fun updateAccountProfile(profile: AccountProfile) {
        currentSettings = currentSettings.copy(
            accountId = profile.accountId,
            accountEmail = profile.email,
            accountDisplayName = profile.displayName
        )
    }

    override suspend fun updateSyncEnabled(enabled: Boolean) {
        currentSettings = currentSettings.copy(syncEnabled = enabled)
    }
}
