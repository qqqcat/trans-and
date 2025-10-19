package com.example.translatorapp.presentation.offline

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.SessionInitializationStatus
import com.example.translatorapp.presentation.theme.LocalSpacing
import com.example.translatorapp.presentation.theme.TranslatorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WhisperDiagnosticsActivity : ComponentActivity() {

    private val viewModel: WhisperDiagnosticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TranslatorTheme {
                WhisperDiagnosticsRoute(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        viewModel.terminateSession()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhisperDiagnosticsRoute(
    viewModel: WhisperDiagnosticsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.stopSession()
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.offline_test_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            StatusSection(uiState = uiState)
            ControlSection(
                uiState = uiState,
                onStart = viewModel::startSession,
                onToggle = viewModel::toggleMicrophone,
                onStop = viewModel::stopSession,
                onClear = viewModel::clearLogs
            )
            LogsSection(uiState = uiState)
        }
    }
}

@Composable
private fun StatusSection(uiState: WhisperDiagnosticsUiState) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        val sessionStatus = if (uiState.isSessionActive) {
            stringResource(id = R.string.offline_test_status_active)
        } else {
            stringResource(id = R.string.offline_test_status_inactive)
        }
        val micStatus = if (uiState.isMicrophoneOpen) {
            stringResource(id = R.string.offline_test_mic_on)
        } else {
            stringResource(id = R.string.offline_test_mic_off)
        }
        Text(
            text = stringResource(id = R.string.offline_test_status_template, sessionStatus, micStatus),
            style = MaterialTheme.typography.bodyLarge
        )
        if (uiState.isBusy) {
            val busyMessage = when (uiState.statusMessage) {
                WhisperDiagnosticsUiState.StatusMessage.Starting -> stringResource(id = R.string.offline_test_busy_starting)
                WhisperDiagnosticsUiState.StatusMessage.Stopping -> stringResource(id = R.string.offline_test_busy_stopping)
                WhisperDiagnosticsUiState.StatusMessage.TogglingMic -> stringResource(id = R.string.offline_test_busy_toggling)
                WhisperDiagnosticsUiState.StatusMessage.Idle, null -> null
            }
            if (!busyMessage.isNullOrBlank()) {
                Text(
                    text = busyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        when (uiState.initializationStatus) {
            SessionInitializationStatus.Downloading -> {
                LinearProgressIndicator(
                    progress = { uiState.initializationProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(
                        id = R.string.offline_test_progress_downloading,
                        (uiState.initializationProgress.coerceIn(0f, 1f) * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SessionInitializationStatus.Preparing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(id = R.string.offline_test_progress_preparing),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SessionInitializationStatus.Ready -> {
                Text(
                    text = stringResource(id = R.string.offline_test_progress_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SessionInitializationStatus.Idle -> Unit
        }

        uiState.lastError?.takeIf { it.isNotBlank() }?.let { error ->
            Text(
                text = stringResource(id = R.string.offline_test_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ControlSection(
    uiState: WhisperDiagnosticsUiState,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        RowOfButtons(
            uiState = uiState,
            onStart = onStart,
            onToggle = onToggle,
            onStop = onStop
        )
        OutlinedButton(
            onClick = onClear,
            enabled = uiState.logs.isNotEmpty()
        ) {
            Text(text = stringResource(id = R.string.offline_test_clear))
        }
    }
}

@Composable
private fun RowOfButtons(
    uiState: WhisperDiagnosticsUiState,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Button(
            onClick = onStart,
            modifier = Modifier.weight(1f),
            enabled = !uiState.isBusy
        ) {
            Text(text = stringResource(id = R.string.offline_test_start))
        }
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.weight(1f),
            enabled = !uiState.isBusy && uiState.isSessionActive
        ) {
            Text(text = stringResource(id = R.string.offline_test_toggle_mic))
        }
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.weight(1f),
            enabled = !uiState.isBusy && uiState.isSessionActive
        ) {
            Text(text = stringResource(id = R.string.offline_test_stop))
        }
    }
}

@Composable
private fun LogsSection(uiState: WhisperDiagnosticsUiState) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(
            text = stringResource(id = R.string.offline_test_logs),
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider()
        if (uiState.logs.isEmpty()) {
            Text(
                text = stringResource(id = R.string.offline_test_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                items(uiState.logs) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}


