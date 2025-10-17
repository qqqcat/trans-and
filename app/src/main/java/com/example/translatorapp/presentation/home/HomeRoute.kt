package com.example.translatorapp.presentation.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.SupportedLanguage
import com.example.translatorapp.domain.model.TranslationInputMode
import com.example.translatorapp.presentation.components.MicrophoneButton
import com.example.translatorapp.presentation.components.PermissionGuidanceCard
import com.example.translatorapp.presentation.components.SessionStatusIndicator
import java.io.InputStream

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    paddingValues: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onPermissionResult
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
        if (bytes != null) {
            val description = queryDisplayName(context, uri)
            viewModel.onImageTranslationRequested(bytes, description)
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeScreen(
        state = uiState,
        paddingValues = paddingValues,
        onToggleMicrophone = viewModel::onToggleMicrophone,
        onStopSession = viewModel::onStopSession,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenPermissionSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        },
        onTextInputChange = viewModel::onTextInputChanged,
        onTranslateText = viewModel::onTranslateText,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onInputModeSelected = viewModel::onInputModeSelected
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    paddingValues: PaddingValues,
    onToggleMicrophone: () -> Unit,
    onStopSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onTextInputChange: (String) -> Unit,
    onTranslateText: () -> Unit,
    onPickImage: () -> Unit,
    onInputModeSelected: (TranslationInputMode) -> Unit,
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SessionStatusIndicator(
            latencyMetrics = state.sessionState.latencyMetrics,
            isMicrophoneActive = state.isMicActive,
            errorMessage = state.errorMessage
        )
        InputModeSelector(
            selected = state.selectedInputMode,
            onSelected = onInputModeSelected
        )
        when (state.selectedInputMode) {
            TranslationInputMode.Voice -> {
                MicrophoneButton(
                    isActive = state.isMicActive,
                    onToggle = onToggleMicrophone,
                    enabled = state.isRecordAudioPermissionGranted
                )
                if (!state.isRecordAudioPermissionGranted) {
                    PermissionGuidanceCard(
                        modifier = Modifier.fillMaxWidth(),
                        onRequestPermission = onRequestPermission,
                        onOpenPermissionSettings = onOpenPermissionSettings
                    )
                }
            }

            TranslationInputMode.Text -> {
                TextTranslationPanel(
                    text = state.textInput,
                    onTextChange = onTextInputChange,
                    onTranslate = onTranslateText,
                    isLoading = state.isTranslatingText,
                    detectedLanguage = state.detectedLanguage,
                    errorMessage = state.manualTranslationError
                )
            }

            TranslationInputMode.Image -> {
                ImageTranslationPanel(
                    isLoading = state.isTranslatingImage,
                    onPickImage = onPickImage,
                    errorMessage = state.manualTranslationError
                )
            }
        }
        Divider()
        Text(
            text = stringResource(id = R.string.subtitle_timeline_title),
            style = MaterialTheme.typography.titleMedium
        )
        if (state.transcriptHistory.isEmpty()) {
            Surface(
                modifier = Modifier.weight(1f, fill = true),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.subtitle_timeline_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                itemsIndexed(state.transcriptHistory) { index, item ->
                    SubtitleTimelineItem(
                        content = item,
                        isFirst = index == 0,
                        isLast = index == state.transcriptHistory.lastIndex
                    )
                }
            }
        }
        ElevatedButton(onClick = onOpenHistory) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = stringResource(id = R.string.home_open_history)
            )
        }
        ElevatedButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = stringResource(id = R.string.home_open_settings)
            )
        }
        Button(onClick = onStopSession) {
            Text(text = stringResource(id = R.string.home_stop_session))
        }
    }
}

@Composable
private fun InputModeSelector(
    selected: TranslationInputMode,
    onSelected: (TranslationInputMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(id = R.string.home_input_mode_label), style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TranslationInputMode.entries.forEach { mode ->
                androidx.compose.material3.FilterChip(
                    selected = mode == selected,
                    onClick = { onSelected(mode) },
                    label = {
                        Text(
                            text = when (mode) {
                                TranslationInputMode.Voice -> stringResource(id = R.string.home_input_mode_voice)
                                TranslationInputMode.Text -> stringResource(id = R.string.home_input_mode_text)
                                TranslationInputMode.Image -> stringResource(id = R.string.home_input_mode_image)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TextTranslationPanel(
    text: String,
    onTextChange: (String) -> Unit,
    onTranslate: () -> Unit,
    isLoading: Boolean,
    detectedLanguage: SupportedLanguage?,
    errorMessage: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text(text = stringResource(id = R.string.home_text_input_label)) }
        )
        androidx.compose.material3.Button(
            onClick = onTranslate,
            enabled = !isLoading
        ) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(text = stringResource(id = R.string.home_translate_text_action))
            }
        }
        detectedLanguage?.let {
            Text(
                text = stringResource(id = R.string.home_detected_language_label, it.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ImageTranslationPanel(
    isLoading: Boolean,
    onPickImage: () -> Unit,
    errorMessage: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onPickImage, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(text = stringResource(id = R.string.home_pick_image_action))
            }
        }
        errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            cursor.getString(index)
        } else {
            null
        }
    }
}
