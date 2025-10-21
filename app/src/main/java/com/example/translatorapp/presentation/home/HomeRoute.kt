package com.example.translatorapp.presentation.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.UiAction
import com.example.translatorapp.domain.model.UiMessage
import com.example.translatorapp.domain.model.UiMessageLevel
import com.example.translatorapp.presentation.components.LanguagePickerSheet
import com.example.translatorapp.presentation.components.LanguagePickerTarget
import com.example.translatorapp.presentation.components.PermissionGuidanceCard
import com.example.translatorapp.presentation.components.SessionStatusIndicator
import com.example.translatorapp.presentation.theme.LocalElevation
import com.example.translatorapp.presentation.theme.LocalRadius
import com.example.translatorapp.presentation.theme.LocalSpacing
import com.example.translatorapp.presentation.theme.WindowBreakpoint
import com.example.translatorapp.presentation.theme.cardPadding
import com.example.translatorapp.presentation.theme.computeBreakpoint
import com.example.translatorapp.presentation.theme.horizontalPadding
import com.example.translatorapp.presentation.theme.sectionSpacing
import java.io.InputStream

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    paddingValues: PaddingValues,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasRequestedPermission = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.session.status, hasRequestedPermission.value, activity) {
        if (state.session.status == SessionStatus.PermissionRequired) {
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: true
            if (!hasRequestedPermission.value || shouldShowRationale) {
                hasRequestedPermission.value = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            hasRequestedPermission.value = false
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
        if (bytes != null) {
            val description = uri.lastPathSegment
            viewModel.onImageTranslationRequested(bytes, description)
        }
    }

    val openPermissionSettings = remember(context) {
        {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    HomeScreen(
        state = state,
        paddingValues = paddingValues,
        onToggleMicrophone = viewModel::onToggleMicrophone,
        onStopSession = viewModel::onStopSession,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenPermissionSettings = openPermissionSettings,
        onOpenHistory = onOpenHistory,
        onTextChanged = viewModel::onTextInputChanged,
        onTranslateText = viewModel::onTranslateText,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onInputModeSelected = viewModel::onInputModeSelected,
        onAutoDetectChanged = viewModel::onAutoDetectChanged,
        onSourceLanguageSelected = viewModel::onSourceLanguageSelected,
        onTargetLanguageSelected = viewModel::onTargetLanguageSelected,
        onDirectionSelected = viewModel::onDirectionSelected,
        onMessageDismissed = viewModel::onMessageDismissed,
        onMessageAction = viewModel::onMessageAction
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    paddingValues: PaddingValues,
    onToggleMicrophone: () -> Unit,
    onStopSession: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onTextChanged: (String) -> Unit,
    onTranslateText: () -> Unit,
    onPickImage: () -> Unit,
    onInputModeSelected: (TranslationInputMode) -> Unit,
    onAutoDetectChanged: (Boolean) -> Unit,
    onSourceLanguageSelected: (SupportedLanguage) -> Unit,
    onTargetLanguageSelected: (SupportedLanguage) -> Unit,
    onDirectionSelected: (LanguageDirection) -> Unit,
    onMessageDismissed: (String) -> Unit,
    onMessageAction: (UiAction) -> Unit
) {
    val bottomPadding = paddingValues.calculateBottomPadding()
    val topPadding = paddingValues.calculateTopPadding()
    val spacing = LocalSpacing.current

    if (state.isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        val breakpoint = computeBreakpoint(maxWidth)
        val horizontalPadding = spacing.horizontalPadding(breakpoint)
        val verticalSpacing = spacing.sectionSpacing(breakpoint)
        val cardPadding = spacing.cardPadding(breakpoint)
        var languagePickerTarget by remember { mutableStateOf(LanguagePickerTarget.Source) }
        var isLanguageSheetVisible by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = verticalSpacing,
                bottom = verticalSpacing + bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            if (state.messages.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        state.messages.forEach { message ->
                            HomeMessageBanner(
                                message = message,
                                onDismiss = { onMessageDismissed(message.id) },
                                onAction = { action -> onMessageAction(action) }
                            )
                        }
                    }
                }
            }

            item {
                SessionStatusCard(
                    session = state.session,
                    breakpoint = breakpoint,
                    cardPadding = cardPadding,
                    onToggleMicrophone = onToggleMicrophone,
                    onStopSession = onStopSession,
                                onOpenLanguagePicker = { target ->
                        languagePickerTarget = target
                        isLanguageSheetVisible = true
                    }
                )
            }

            if (state.session.status == SessionStatus.PermissionRequired) {
                item {
                    PermissionGuidanceCard(
                        modifier = Modifier.fillMaxWidth(),
                        onRequestPermission = onRequestPermission,
                        onOpenPermissionSettings = onOpenPermissionSettings
                    )
                }
            }

            item {
                InputModeSelector(
                    selected = state.input.selectedMode,
                    breakpoint = breakpoint,
                    onInputModeSelected = onInputModeSelected
                )
            }

            when (state.input.selectedMode) {
                TranslationInputMode.Voice -> {
                    item {
                        VoiceModeCard(
                            session = state.session,
                            breakpoint = breakpoint,
                            cardPadding = cardPadding
                        )
                    }
                }

                TranslationInputMode.Text -> {
                    item {
                        TextInputCard(
                            state = state.input,
                            breakpoint = breakpoint,
                            cardPadding = cardPadding,
                            onTextChanged = onTextChanged,
                            onTranslateText = onTranslateText
                        )
                    }
                }

                TranslationInputMode.Image -> {
                    item {
                        ImageInputCard(
                            state = state.input,
                            breakpoint = breakpoint,
                            cardPadding = cardPadding,
                            onPickImage = onPickImage
                        )
                    }
                }
            }

            if (state.timeline.entries.isNotEmpty()) {
                item {
                    TimelineHeader(onOpenHistory = onOpenHistory, breakpoint = breakpoint)
                }
            }

            if (state.timeline.entries.isEmpty()) {
                item { HistoryPreviewCard(onOpenHistory = onOpenHistory, breakpoint = breakpoint) }
                item { EmptyTimelinePlaceholder() }
            } else {
                itemsIndexed(state.timeline.entries) { index, item ->
                    SubtitleTimelineItem(
                        content = item,
                        isFirst = index == 0,
                        isLast = index == state.timeline.entries.lastIndex
                    )
                }
            }
        }

        if (isLanguageSheetVisible) {
            LanguagePickerSheet(
                target = languagePickerTarget,
                currentDirection = state.settings.direction,
                availableLanguages = SupportedLanguage.entries,
                favorites = emptyList(),
                isAutoDetectEnabled = state.settings.direction.isAutoDetect,
                onDismiss = { isLanguageSheetVisible = false },
                onTargetChange = { languagePickerTarget = it },
                onFavoriteSelected = { direction ->
                    onDirectionSelected(direction)
                    isLanguageSheetVisible = false
                },
                onSourceSelected = { language ->
                    if (language == null) {
                        if (!state.settings.direction.isAutoDetect) {
                            onAutoDetectChanged(true)
                        }
                    } else {
                        if (state.settings.direction.isAutoDetect) {
                            onAutoDetectChanged(false)
                        }
                        onSourceLanguageSelected(language)
                    }
                    isLanguageSheetVisible = false
                },
                onTargetSelected = { language ->
                    onTargetLanguageSelected(language)
                    isLanguageSheetVisible = false
                }
            )
        }
    }
}

@Composable
private fun HomeMessageBanner(
    message: UiMessage,
    onDismiss: () -> Unit,
    onAction: (UiAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (message.level) {
        UiMessageLevel.Info -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        UiMessageLevel.Success -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        UiMessageLevel.Warning -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        UiMessageLevel.Error -> colorScheme.errorContainer to colorScheme.onErrorContainer
    }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                }
            }
            AnimatedVisibility(visible = message.action != null && message.actionLabel != null) {
                TextButton(
                    onClick = {
                        message.action?.let {
                            onAction(it)
                            onDismiss()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Text(text = message.actionLabel ?: "")
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStatusCard(
    session: SessionUiState,
    breakpoint: WindowBreakpoint,
    cardPadding: Dp,
    onToggleMicrophone: () -> Unit,
    onStopSession: () -> Unit,
    onOpenLanguagePicker: (LanguagePickerTarget) -> Unit
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    val isCompact = breakpoint == WindowBreakpoint.Compact
    val micBusy = session.isMicActionInProgress
    val stopBusy = session.isStopInProgress

    val (statusLabel, statusIcon, statusColor) = when (session.status) {
        SessionStatus.Streaming -> Triple(
            stringResource(id = R.string.home_status_active),
            Icons.Outlined.GraphicEq,
            colorScheme.primary
        )
        SessionStatus.Connecting -> Triple(
            stringResource(id = R.string.home_status_connecting),
            Icons.Outlined.Sync,
            colorScheme.tertiary
        )
        SessionStatus.PermissionRequired -> Triple(
            stringResource(id = R.string.home_status_permission),
            Icons.Outlined.Lock,
            colorScheme.error
        )
        SessionStatus.Error -> Triple(
            session.lastErrorMessage ?: stringResource(id = R.string.home_status_inactive),
            Icons.Outlined.ErrorOutline,
            colorScheme.error
        )
        SessionStatus.Idle -> Triple(
            stringResource(id = R.string.home_status_inactive),
            Icons.Outlined.MicOff,
            colorScheme.onSurfaceVariant
        )
    }

    val sourceLabel = session.direction.sourceLanguage?.displayName
        ?: stringResource(id = R.string.home_language_auto_detect)
    val targetLabel = session.direction.targetLanguage.displayName

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(imageVector = statusIcon, contentDescription = null, tint = statusColor)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Text(text = statusLabel, style = MaterialTheme.typography.titleMedium, color = statusColor)
                    LanguageSummaryCard(
                        sourceLabel = sourceLabel,
                        targetLabel = targetLabel,
                        onSourceClick = { onOpenLanguagePicker(LanguagePickerTarget.Source) },
                        onTargetClick = { onOpenLanguagePicker(LanguagePickerTarget.Target) }
                    )
                    Text(
                        text = stringResource(id = R.string.home_model_label, session.model.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SessionStatusIndicator(
                latencyMetrics = session.latency,
                initializationStatus = session.initializationStatus,
                initializationProgress = session.initializationProgress,
                isMicrophoneActive = session.isMicrophoneOpen,
                errorMessage = session.lastErrorMessage
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val micLabel = if (session.isMicrophoneOpen) {
                    stringResource(id = R.string.home_action_mute)
                } else {
                    stringResource(id = R.string.home_action_unmute)
                }
                val micIcon = if (session.isMicrophoneOpen) Icons.Outlined.MicOff else Icons.Outlined.Mic
                Button(
                    onClick = onToggleMicrophone,
                    enabled = session.status != SessionStatus.PermissionRequired && !micBusy,
                    modifier = if (isCompact) Modifier.weight(1f) else Modifier
                ) {
                    if (micBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(spacing.md),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(text = stringResource(id = R.string.home_action_processing))
                    } else {
                        Icon(imageVector = micIcon, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(text = micLabel)
                    }
                }
                OutlinedButton(
                    onClick = onStopSession,
                    enabled = (session.status == SessionStatus.Streaming || session.status == SessionStatus.Connecting) && !stopBusy,
                    modifier = if (isCompact) Modifier.weight(1f) else Modifier
                ) {
                    if (stopBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(spacing.md),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(text = stringResource(id = R.string.home_stop_session))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputModeSelector(
    selected: TranslationInputMode,
    breakpoint: WindowBreakpoint,
    onInputModeSelected: (TranslationInputMode) -> Unit
) {
    val spacing = LocalSpacing.current
    val cardPadding = spacing.cardPadding(breakpoint)
    val primaryModes = listOf(TranslationInputMode.Voice, TranslationInputMode.Text)
    val secondaryModes = TranslationInputMode.entries.filterNot { it in primaryModes }
    var isSheetOpen by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(id = R.string.home_input_mode_label),
                style = MaterialTheme.typography.titleSmall
            )
            ModeOption(
                mode = TranslationInputMode.Voice,
                selected = selected == TranslationInputMode.Voice,
                onClick = { onInputModeSelected(TranslationInputMode.Voice) }
            )
            ModeOption(
                mode = TranslationInputMode.Text,
                selected = selected == TranslationInputMode.Text,
                onClick = { onInputModeSelected(TranslationInputMode.Text) }
            )
            if (secondaryModes.isNotEmpty()) {
                OutlinedButton(
                    onClick = { isSheetOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(id = R.string.home_input_mode_more)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(text = stringResource(id = R.string.home_input_mode_more))
                    if (selected in secondaryModes) {
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(
                            text = stringResource(id = selected.labelRes),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (selected !in primaryModes && selected in TranslationInputMode.entries) {
                Text(
                    text = stringResource(id = selected.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (secondaryModes.isNotEmpty() && isSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { isSheetOpen = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Text(
                    text = stringResource(id = R.string.home_input_mode_more_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = spacing.lg)
                )
                secondaryModes.forEach { mode ->
                    val isSelected = mode == selected
                    ListItem(
                        headlineContent = { Text(text = stringResource(id = mode.labelRes)) },
                        supportingContent = {
                            Text(
                                text = stringResource(id = mode.descriptionRes),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            RadioButton(selected = isSelected, onClick = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onInputModeSelected(mode)
                                isSheetOpen = false
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    mode: TranslationInputMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    val shape = MaterialTheme.shapes.medium
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        color = containerColor,
        tonalElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Icon(imageVector = mode.icon, contentDescription = null, tint = contentColor)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = mode.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Text(
                    text = stringResource(id = mode.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
            if (selected) {
                Icon(imageVector = Icons.Outlined.Check, contentDescription = null, tint = contentColor)
            }
        }
    }
}

private val TranslationInputMode.labelRes: Int
    get() = when (this) {
        TranslationInputMode.Voice -> R.string.home_input_mode_voice
        TranslationInputMode.Text -> R.string.home_input_mode_text
        TranslationInputMode.Image -> R.string.home_input_mode_image
    }

private val TranslationInputMode.descriptionRes: Int
    get() = when (this) {
        TranslationInputMode.Voice -> R.string.home_input_mode_voice_subtitle
        TranslationInputMode.Text -> R.string.home_input_mode_text_subtitle
        TranslationInputMode.Image -> R.string.home_input_mode_image_subtitle
    }

private val TranslationInputMode.icon: ImageVector
    get() = when (this) {
        TranslationInputMode.Voice -> Icons.Outlined.Mic
        TranslationInputMode.Text -> Icons.Outlined.Edit
        TranslationInputMode.Image -> Icons.Outlined.Image
    }

@Composable
private fun InputModeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.height(if (selected) 24.dp else 22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSummaryCard(
    sourceLabel: String,
    targetLabel: String,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSourceClick),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_language_tab_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(text = "→", style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTargetClick),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = stringResource(id = R.string.settings_language_tab_target),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VoiceModeCard(
    session: SessionUiState,
    breakpoint: WindowBreakpoint,
    cardPadding: Dp
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = stringResource(id = R.string.home_input_mode_voice),
                style = MaterialTheme.typography.titleMedium
            )
            val description = if (session.isMicrophoneOpen) {
                stringResource(id = R.string.home_voice_active_description)
            } else {
                stringResource(id = R.string.home_voice_inactive_description)
            }
            val maxLines = if (breakpoint == WindowBreakpoint.Compact) 3 else Int.MAX_VALUE
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines
            )
            HorizontalDivider()
            SessionStatusIndicator(
                latencyMetrics = session.latency,
                initializationStatus = session.initializationStatus,
                initializationProgress = session.initializationProgress,
                isMicrophoneActive = session.isMicrophoneOpen,
                errorMessage = session.lastErrorMessage
            )
        }
    }
}

@Composable
private fun TextInputCard(
    state: InputUiState,
    breakpoint: WindowBreakpoint,
    cardPadding: Dp,
    onTextChanged: (String) -> Unit,
    onTranslateText: () -> Unit
) {
    val spacing = LocalSpacing.current
    val isCompact = breakpoint == WindowBreakpoint.Compact
    val minLines = if (isCompact) 4 else 6
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(id = R.string.home_text_translation_title),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.textValue,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(id = R.string.home_text_input_label)) },
                minLines = minLines,
                maxLines = minLines + 2
            )
            state.detectedLanguage?.let { detected ->
                Text(
                    text = stringResource(id = R.string.home_detected_language_label, detected.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onTranslateText,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !state.isTextTranslating
            ) {
                if (state.isTextTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = spacing.xs)
                            .height(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(text = stringResource(id = R.string.home_translate_text_action))
            }
        }
    }
}

@Composable
private fun ImageInputCard(
    state: InputUiState,
    breakpoint: WindowBreakpoint,
    cardPadding: Dp,
    onPickImage: () -> Unit
) {
    val spacing = LocalSpacing.current
    val isCompact = breakpoint == WindowBreakpoint.Compact
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(id = R.string.home_image_translation_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.home_image_translation_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onPickImage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !state.isImageTranslating
            ) {
                if (state.isImageTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = spacing.xs)
                            .height(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(text = stringResource(id = R.string.home_pick_image_action))
            }
        }
    }
}

@Composable
private fun HistoryPreviewCard(
    onOpenHistory: () -> Unit,
    breakpoint: WindowBreakpoint
) {
    val spacing = LocalSpacing.current
    val cardPadding = spacing.cardPadding(breakpoint)
    val isCompact = breakpoint == WindowBreakpoint.Compact
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.home_history_preview_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = stringResource(id = R.string.home_history_preview_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(id = R.string.home_open_history))
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    onOpenHistory: () -> Unit,
    breakpoint: WindowBreakpoint
) {
    val spacing = LocalSpacing.current
    val isCompact = breakpoint == WindowBreakpoint.Compact
    if (isCompact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.subtitle_timeline_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.home_timeline_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onOpenHistory) {
                Text(text = stringResource(id = R.string.home_open_history))
                Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.subtitle_timeline_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(id = R.string.home_timeline_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenHistory) {
                Text(text = stringResource(id = R.string.home_open_history))
                Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun EmptyTimelinePlaceholder() {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = stringResource(id = R.string.subtitle_timeline_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
