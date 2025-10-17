package com.example.translatorapp.presentation.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.domain.model.UiAction
import com.example.translatorapp.domain.model.UiMessage
import com.example.translatorapp.domain.model.UiMessageLevel
import com.example.translatorapp.presentation.components.PermissionGuidanceCard
import com.example.translatorapp.presentation.components.SessionStatusIndicator
import com.example.translatorapp.presentation.theme.LocalElevation
import com.example.translatorapp.presentation.theme.LocalRadius
import com.example.translatorapp.presentation.theme.LocalSpacing
import java.io.InputStream

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    paddingValues: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

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
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onTextChanged = viewModel::onTextInputChanged,
        onTranslateText = viewModel::onTranslateText,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onInputModeSelected = viewModel::onInputModeSelected,
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
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onTextChanged: (String) -> Unit,
    onTranslateText: () -> Unit,
    onPickImage: () -> Unit,
    onInputModeSelected: (TranslationInputMode) -> Unit,
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.lg,
            bottom = spacing.xxl + bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
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
                onToggleMicrophone = onToggleMicrophone,
                onStopSession = onStopSession,
                onOpenSettings = onOpenSettings
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
                onInputModeSelected = onInputModeSelected
            )
        }

        when (state.input.selectedMode) {
            TranslationInputMode.Voice -> {
                item {
                    VoiceModeCard(
                        session = state.session
                    )
                }
            }

            TranslationInputMode.Text -> {
                item {
                    TextInputCard(
                        state = state.input,
                        onTextChanged = onTextChanged,
                        onTranslateText = onTranslateText
                    )
                }
            }

            TranslationInputMode.Image -> {
                item {
                    ImageInputCard(
                        state = state.input,
                        onPickImage = onPickImage
                    )
                }
            }
        }

        item {
            HistoryPreviewCard(onOpenHistory = onOpenHistory)
        }

        item {
            TimelineHeader(onOpenHistory = onOpenHistory)
        }

        if (state.timeline.entries.isEmpty()) {
            item {
                EmptyTimelinePlaceholder()
            }
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
    onToggleMicrophone: () -> Unit,
    onStopSession: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme

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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor
                )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )
            AssistChip(
                    onClick = {},
                    label = {
                        Text(text = "${sourceLabel} → ${targetLabel}")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = null
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }

            Text(
                text = stringResource(id = R.string.home_model_label, session.model.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SessionStatusIndicator(
                latencyMetrics = session.latency,
                isMicrophoneActive = session.isMicrophoneOpen,
                errorMessage = session.lastErrorMessage
            )

            Row(
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
                    enabled = session.status != SessionStatus.PermissionRequired
                ) {
                    Icon(imageVector = micIcon, contentDescription = null)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(text = micLabel)
                }
                OutlinedButton(
                    onClick = onStopSession,
                    enabled = session.status == SessionStatus.Streaming || session.status == SessionStatus.Connecting
                ) {
                    Text(text = stringResource(id = R.string.home_stop_session))
                }
                TextButton(onClick = onOpenSettings) {
                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(text = stringResource(id = R.string.home_open_settings))
                }
            }
        }
    }
}

@Composable
private fun InputModeSelector(
    selected: TranslationInputMode,
    onInputModeSelected: (TranslationInputMode) -> Unit
) {
    val spacing = LocalSpacing.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(id = R.string.home_input_mode_label),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                InputModeChip(
                    label = stringResource(id = R.string.home_input_mode_voice),
                    icon = Icons.Outlined.Mic,
                    selected = selected == TranslationInputMode.Voice,
                    onClick = { onInputModeSelected(TranslationInputMode.Voice) }
                )
                InputModeChip(
                    label = stringResource(id = R.string.home_input_mode_text),
                    icon = Icons.Outlined.Edit,
                    selected = selected == TranslationInputMode.Text,
                    onClick = { onInputModeSelected(TranslationInputMode.Text) }
                )
                InputModeChip(
                    label = stringResource(id = R.string.home_input_mode_image),
                    icon = Icons.Outlined.Image,
                    selected = selected == TranslationInputMode.Image,
                    onClick = { onInputModeSelected(TranslationInputMode.Image) }
                )
            }
        }
    }
}

@Composable
private fun InputModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text = label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            labelColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconContentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun VoiceModeCard(
    session: SessionUiState
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
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
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            SessionStatusIndicator(
                latencyMetrics = session.latency,
                isMicrophoneActive = session.isMicrophoneOpen,
                errorMessage = session.lastErrorMessage
            )
        }
    }
}

@Composable
private fun TextInputCard(
    state: InputUiState,
    onTextChanged: (String) -> Unit,
    onTranslateText: () -> Unit
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(id = R.string.home_text_translation_title),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.textValue,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text(text = stringResource(id = R.string.home_text_input_label)) }
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
                modifier = Modifier.align(Alignment.End),
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
    onPickImage: () -> Unit
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
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
                modifier = Modifier.align(Alignment.End),
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
    onOpenHistory: () -> Unit
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
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
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(id = R.string.home_open_history))
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    onOpenHistory: () -> Unit
) {
    val spacing = LocalSpacing.current
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
