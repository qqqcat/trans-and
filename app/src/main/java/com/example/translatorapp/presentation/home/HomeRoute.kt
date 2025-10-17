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
import com.example.translatorapp.presentation.components.MicrophoneButton
import com.example.translatorapp.presentation.components.PermissionGuidanceCard
import com.example.translatorapp.presentation.components.SessionStatusIndicator

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
        }
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
