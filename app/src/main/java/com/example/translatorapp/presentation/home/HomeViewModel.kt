package com.example.translatorapp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.SessionInitializationStatus
import com.example.translatorapp.domain.model.TranslatorException
import com.example.translatorapp.domain.model.UiAction
import com.example.translatorapp.domain.model.UiMessage
import com.example.translatorapp.domain.model.UiMessageLevel
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.domain.model.defaultLabel
import com.example.translatorapp.domain.usecase.DetectLanguageUseCase
import com.example.translatorapp.domain.usecase.LoadSettingsUseCase
import com.example.translatorapp.domain.usecase.ObserveLiveTranscriptionUseCase
import com.example.translatorapp.domain.usecase.ObserveSessionStateUseCase
import com.example.translatorapp.domain.usecase.PersistHistoryUseCase
import com.example.translatorapp.domain.usecase.StartRealtimeSessionUseCase
import com.example.translatorapp.domain.usecase.StopRealtimeSessionUseCase
import com.example.translatorapp.domain.usecase.ToggleMicrophoneUseCase
import com.example.translatorapp.domain.usecase.TranslateImageUseCase
import com.example.translatorapp.domain.usecase.TranslateTextUseCase
import com.example.translatorapp.domain.usecase.UpdateDirectionUseCase
import com.example.translatorapp.domain.usecase.UpdateModelUseCase
import com.example.translatorapp.util.DispatcherProvider
import com.example.translatorapp.util.PermissionManager
import com.example.translatorapp.data.realtime.RealtimeSessionManager
import com.example.translatorapp.audio.AudioSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TIMELINE_MAX_ITEMS = 100

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeSessionState: ObserveSessionStateUseCase,
    private val observeLiveTranscription: ObserveLiveTranscriptionUseCase,
    private val startRealtimeSession: StartRealtimeSessionUseCase,
    private val stopRealtimeSession: StopRealtimeSessionUseCase,
    private val toggleMicrophoneUseCase: ToggleMicrophoneUseCase,
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val updateDirectionUseCase: UpdateDirectionUseCase,
    private val updateModelUseCase: UpdateModelUseCase,
    private val persistHistoryUseCase: PersistHistoryUseCase,
    private val translateTextUseCase: TranslateTextUseCase,
    private val translateImageUseCase: TranslateImageUseCase,
    private val detectLanguageUseCase: DetectLanguageUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val permissionManager: PermissionManager,
    private val realtimeSessionManager: RealtimeSessionManager,
    private val audioSessionController: AudioSessionController
) : ViewModel() {

    // WebRTC迁移：会话与音频链路控制
    fun startWebRtcSession(model: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val ok = realtimeSessionManager.startSession(model)
                if (ok) {
                    _uiState.update { it.copy(isLoading = false, session = it.session.copy(status = SessionStatus.Streaming)) }
                } else {
                    _uiState.update { it.copy(isLoading = false, session = it.session.copy(status = SessionStatus.Idle, lastErrorMessage = "WebRTC会话启动失败")) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, session = it.session.copy(status = SessionStatus.Idle, lastErrorMessage = e.message)) }
            }
        }
    }

    fun stopWebRtcSession() {
        realtimeSessionManager.close()
        _uiState.update { it.copy(session = it.session.copy(status = SessionStatus.Idle)) }
    }

    fun startWebRtcRecording() {
        _uiState.update { it.copy(session = it.session.copy(isMicrophoneOpen = true)) }
        audioSessionController.startCapture { buffer ->
            // TODO: 推送 buffer 到 WebRTC 音频轨道
        }
    }

    fun stopWebRtcRecording() {
        audioSessionController.stopCapture()
        _uiState.update { it.copy(session = it.session.copy(isMicrophoneOpen = false)) }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentSettings: UserSettings? = null
    private var latestSessionState: TranslationSessionState = TranslationSessionState()
    private var hasStartedSession = false
    private var isStartingSession = false
    private var settingsLoaded = false
    private var permissionChecked = false
    private var manualSourceLanguage: SupportedLanguage = SupportedLanguage.ChineseSimplified

    init {
        observeSessionChanges()
        observeTranscriptChanges()
        loadInitialSettings()
        refreshPermissionStatus()
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            observeSessionState().collect { session ->
                latestSessionState = session
                if (session.isActive) {
                    isStartingSession = false
                }
                if (session.errorMessage != null) {
                    isStartingSession = false
                }
                hasStartedSession = session.isActive
                _uiState.update { current ->
                    var updatedSession = current.session.copy(
                        direction = session.direction,
                        latency = session.latencyMetrics,
                        initializationStatus = session.initializationStatus,
                        initializationProgress = session.initializationProgress,
                        isMicrophoneOpen = session.isMicrophoneOpen,
                        activeSegment = session.currentSegment,
                        lastErrorMessage = session.errorMessage
                    )
                    val pendingMic = updatedSession.pendingMicState
                    if (pendingMic != null && session.isMicrophoneOpen == pendingMic) {
                        updatedSession = updatedSession.copy(
                            isMicActionInProgress = false,
                            pendingMicState = null
                        )
                    } else if (session.errorMessage != null) {
                        updatedSession = updatedSession.copy(
                            isMicActionInProgress = false,
                            pendingMicState = null
                        )
                    }
                    if (!session.isActive) {
                        updatedSession = updatedSession.copy(isStopInProgress = false)
                    }
                    val snapshot = current.copy(session = updatedSession)
                    snapshot.copy(
                        session = snapshot.session.copy(
                            status = computeSessionStatus(snapshot),
                            requiresPermission = !snapshot.input.isRecordAudioPermissionGranted
                        )
                    )
                }
                session.errorMessage?.let { pushMessage(it) }
            }
        }
    }

    private fun observeTranscriptChanges() {
        viewModelScope.launch(dispatcherProvider.io) {
            observeLiveTranscription().collect { content ->
                if (content.transcript.isBlank() && content.translation.isBlank()) {
                    return@collect
                }
                persistHistoryUseCase(content)
                _uiState.update { current ->
                    val updatedTimeline = current.timeline.copy(
                        entries = (current.timeline.entries + content).takeLast(TIMELINE_MAX_ITEMS)
                    )
                    val updatedInput = current.input.copy(
                        detectedLanguage = content.detectedSourceLanguage,
                        selectedMode = TranslationInputMode.Voice
                    )
                    val snapshot = current.copy(
                        timeline = updatedTimeline,
                        input = updatedInput
                    )
                    snapshot.copy(
                        session = snapshot.session.copy(
                            status = computeSessionStatus(snapshot)
                        )
                    )
                }
            }
        }
    }

    private fun loadInitialSettings() {
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = loadSettingsUseCase()
            applySettings(settings)
            settingsLoaded = true
            finalizeInitialization()
        }
    }

    fun onAutoDetectChanged(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = currentSettings ?: loadSettingsUseCase()
            val baseDirection = settings.direction
            val updated = if (enabled) {
                baseDirection.withSource(null)
            } else {
                baseDirection.withSource(manualSourceLanguage)
            }
            updateDirectionUseCase(updated)
            reloadSettings()
        }
    }

    fun onSourceLanguageSelected(language: SupportedLanguage) {
        manualSourceLanguage = language
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = currentSettings ?: loadSettingsUseCase()
            val direction = settings.direction.withSource(language)
            updateDirectionUseCase(direction)
            reloadSettings()
        }
    }

    fun onTargetLanguageSelected(language: SupportedLanguage) {
        viewModelScope.launch(dispatcherProvider.io) {
            val settings = currentSettings ?: loadSettingsUseCase()
            val direction = settings.direction.withTarget(language)
            updateDirectionUseCase(direction)
            reloadSettings()
        }
    }

    fun onDirectionSelected(direction: LanguageDirection) {
        viewModelScope.launch(dispatcherProvider.io) {
            manualSourceLanguage = direction.sourceLanguage ?: manualSourceLanguage
            updateDirectionUseCase(direction)
            reloadSettings()
        }
    }

    fun onModelSelected(profile: TranslationModelProfile) {
        viewModelScope.launch(dispatcherProvider.io) {
            updateModelUseCase(profile)
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val refreshed = loadSettingsUseCase()
        applySettings(refreshed)
    }

    private fun applySettings(settings: UserSettings) {
        currentSettings = settings
        manualSourceLanguage = settings.direction.sourceLanguage ?: manualSourceLanguage
        _uiState.update { current ->
            val snapshot = current.copy(
                settings = settings,
                session = current.session.copy(
                    direction = settings.direction,
                    model = settings.translationProfile
                )
            )
            snapshot.copy(
                session = snapshot.session.copy(
                    status = computeSessionStatus(snapshot)
                )
            )
        }
    }

    fun refreshPermissionStatus() {
        viewModelScope.launch(dispatcherProvider.io) {
            val granted = permissionManager.hasRecordAudioPermission()
            permissionChecked = true
            updateInput { it.copy(isRecordAudioPermissionGranted = granted) }
            finalizeInitialization()
            if (granted) {
                maybeStartSession()
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        permissionChecked = true
        updateInput { it.copy(isRecordAudioPermissionGranted = isGranted) }
        finalizeInitialization()
        if (isGranted) {
            maybeStartSession()
        } else {
            isStartingSession = false
            hasStartedSession = false
            latestSessionState = TranslationSessionState()
            viewModelScope.launch(dispatcherProvider.io) {
                stopRealtimeSession()
            }
        }
    }

    fun onToggleMicrophone() {
        if (!uiState.value.input.isRecordAudioPermissionGranted) {
            pushMessage(
                message = "请先授予麦克风权限以启动实时翻译",
                level = UiMessageLevel.Warning,
                action = UiAction.CheckPermissions
            )
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            val targetState = !uiState.value.session.isMicrophoneOpen
            val currentState = uiState.value.session.isMicrophoneOpen
            updateSession {
                it.copy(
                    isMicActionInProgress = true,
                    pendingMicState = targetState,
                    isMicrophoneOpen = targetState
                )
            }
            runCatching { toggleMicrophoneUseCase() }
                .onSuccess { }
                .onFailure { throwable ->
                    updateSession {
                        it.copy(
                            isMicActionInProgress = false,
                            pendingMicState = null,
                            isMicrophoneOpen = currentState
                        )
                    }
                    pushMessageFromThrowable(throwable, fallback = "Failed to toggle microphone")
                }
        }
    }

    fun onStopSession() {
        viewModelScope.launch(dispatcherProvider.io) {
            val result = runCatching { stopRealtimeSession() }
            hasStartedSession = false
            isStartingSession = false
            latestSessionState = TranslationSessionState()
            updateSession {
                it.copy(
                    isMicrophoneOpen = false,
                    activeSegment = null,
                    lastErrorMessage = null,
                    status = SessionStatus.Idle,
                    initializationStatus = SessionInitializationStatus.Idle,
                    initializationProgress = 0f,
                    isStopInProgress = true,
                    pendingMicState = null
                )
            }
            result.onFailure { throwable ->
                updateSession {
                    it.copy(isStopInProgress = false)
                }
                pushMessageFromThrowable(throwable, fallback = "Failed to stop session")
            }
        }
    }

    fun onInputModeSelected(mode: TranslationInputMode) {
        updateInput { it.copy(selectedMode = mode) }
        if (mode == TranslationInputMode.Voice) {
            maybeStartSession()
        }
    }

    fun onTextInputChanged(value: String) {
        updateInput { it.copy(textValue = value) }
    }

    fun onTranslateText() {
        val text = uiState.value.input.textValue.trim()
        if (text.isBlank()) {
            pushMessage(
                message = "请输入需要翻译的文本",
                level = UiMessageLevel.Warning,
                action = UiAction.Dismiss
            )
            return
        }
        val settings = currentSettings ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            updateInput { it.copy(isTextTranslating = true) }
            val direction = resolveDirectionForText(text)
            runCatching {
                translateTextUseCase(text, direction, settings.translationProfile)
            }.onSuccess { content ->
                updateInput {
                    it.copy(
                        textValue = "",
                        isTextTranslating = false,
                        detectedLanguage = content.detectedSourceLanguage,
                        selectedMode = TranslationInputMode.Text
                    )
                }
                updateTimeline { timeline ->
                    timeline.copy(
                        entries = (timeline.entries + content).takeLast(TIMELINE_MAX_ITEMS)
                    )
                }
            }.onFailure { throwable ->
                updateInput { it.copy(isTextTranslating = false) }
                pushMessageFromThrowable(throwable, fallback = "文本翻译失败")
            }
        }
    }

    fun onImageTranslationRequested(imageBytes: ByteArray, description: String?) {
        val settings = currentSettings ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            updateInput { it.copy(isImageTranslating = true, detectedLanguage = null) }
            runCatching {
                translateImageUseCase(imageBytes, settings.direction, settings.translationProfile)
            }.onSuccess { content ->
                val normalized = if (content.transcript.isBlank() && !description.isNullOrBlank()) {
                    content.copy(transcript = description)
                } else {
                    content
                }
                updateInput {
                    it.copy(
                        isImageTranslating = false,
                        detectedLanguage = normalized.detectedSourceLanguage,
                        selectedMode = TranslationInputMode.Image
                    )
                }
                updateTimeline { timeline ->
                    timeline.copy(
                        entries = (timeline.entries + normalized).takeLast(TIMELINE_MAX_ITEMS)
                    )
                }
            }.onFailure { throwable ->
                updateInput { it.copy(isImageTranslating = false) }
                pushMessageFromThrowable(throwable, fallback = "图片翻译失败")
            }
        }
    }

    fun onMessageDismissed(id: String) {
        _uiState.update { current ->
            current.copy(messages = current.messages.filterNot { it.id == id })
        }
    }

    fun onMessageAction(action: UiAction) {
        when (action) {
            UiAction.Retry -> maybeStartSession()
            UiAction.CheckPermissions -> refreshPermissionStatus()
            UiAction.OpenSettings, UiAction.Dismiss -> Unit
        }
    }

    private fun maybeStartSession() {
        if (!_uiState.value.input.isRecordAudioPermissionGranted || hasStartedSession) {
            return
        }
        hasStartedSession = true
        viewModelScope.launch(dispatcherProvider.io) {
            val settingsResult = runCatching { loadSettingsUseCase() }
            val settings = settingsResult.getOrElse { throwable ->
                hasStartedSession = false
                pushMessage(throwable.message ?: "无法加载设置，已停止实时会话")
                return@launch
            }
            currentSettings = settings
            _uiState.update { it.copy(settings = settings) }
            if (!settings.translationProfile.supportsRealtime) {
                hasStartedSession = false
                pushMessage("当前选择的模型不支持实时翻译，请在设置中切换实时模型")
                return@launch
            }
            val result = runCatching { startRealtimeSession(settings) }
            result.onFailure { error ->
                hasStartedSession = false
                pushMessage(error.message ?: "实时会话启动失败")
            }
        }
    }

    private fun updateInput(transform: (InputUiState) -> InputUiState) {
        _uiState.update { current ->
            val updatedInput = transform(current.input)
            val snapshot = current.copy(input = updatedInput)
            snapshot.copy(
                session = snapshot.session.copy(
                    status = computeSessionStatus(snapshot),
                    requiresPermission = !snapshot.input.isRecordAudioPermissionGranted
                )
            )
        }
    }

    private fun updateSession(transform: (SessionUiState) -> SessionUiState) {
        _uiState.update { current ->
            val updatedSession = transform(current.session)
            val snapshot = current.copy(session = updatedSession)
            snapshot.copy(
                session = snapshot.session.copy(
                    status = computeSessionStatus(snapshot),
                    requiresPermission = !snapshot.input.isRecordAudioPermissionGranted
                )
            )
        }
    }

    private fun updateTimeline(transform: (TimelineUiState) -> TimelineUiState) {
        _uiState.update { current ->
            current.copy(timeline = transform(current.timeline))
        }
    }

    private fun recalculateStatus() {
        _uiState.update { current ->
            current.copy(
                session = current.session.copy(
                    status = computeSessionStatus(current),
                    requiresPermission = !current.input.isRecordAudioPermissionGranted
                )
            )
        }
    }

    private fun computeSessionStatus(snapshot: HomeUiState): SessionStatus {
        val hasPermission = snapshot.input.isRecordAudioPermissionGranted
        val session = latestSessionState
        if (session.errorMessage != null) {
            isStartingSession = false
        }
        return when {
            !hasPermission -> SessionStatus.PermissionRequired
            session.errorMessage != null -> SessionStatus.Error
            session.isActive -> {
                isStartingSession = false
                SessionStatus.Streaming
            }
            isStartingSession -> SessionStatus.Connecting
            else -> SessionStatus.Idle
        }
    }

    private suspend fun resolveDirectionForText(text: String): LanguageDirection {
        val settings = currentSettings ?: return UserSettings().direction
        if (!settings.direction.isAutoDetect) {
            return settings.direction
        }
        updateInput { it.copy(detectedLanguage = null) }
        val detected = detectLanguageUseCase(text)
        updateInput { it.copy(detectedLanguage = detected) }
        return if (detected != null) {
            settings.direction.withSource(detected)
        } else {
            settings.direction
        }
    }

    private fun pushMessageFromThrowable(throwable: Throwable, fallback: String) {
        val exception = throwable.asTranslatorException(fallback)
        pushMessage(
            message = exception.userMessage.ifBlank { fallback },
            level = exception.level,
            action = exception.action
        )
    }

    private fun pushMessage(
        message: String,
        level: UiMessageLevel = UiMessageLevel.Error,
        action: UiAction? = null
    ) {
        val uiMessage = UiMessage(
            message = message,
            level = level,
            action = action,
            actionLabel = action?.defaultLabel()
        )
        _uiState.update { current -> current.copy(messages = current.messages + uiMessage) }
    }

    private fun Throwable.asTranslatorException(fallback: String): TranslatorException =
        when (this) {
            is TranslatorException -> this
            else -> TranslatorException(
                userMessage = message ?: fallback,
                action = UiAction.Retry,
                level = UiMessageLevel.Error,
                cause = this
            )
        }

    private fun finalizeInitialization() {
        if (settingsLoaded && permissionChecked) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching { stopRealtimeSession() }
        }
        hasStartedSession = false
        isStartingSession = false
        super.onCleared()
    }
}
