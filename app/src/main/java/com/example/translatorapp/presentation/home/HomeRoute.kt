package com.example.translatorapp.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.translatorapp.presentation.components.MicrophoneButton
import com.example.translatorapp.presentation.components.SessionStatusIndicator

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    paddingValues: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreen(
        state = uiState,
        paddingValues = paddingValues,
        onToggleMicrophone = viewModel::onToggleMicrophone,
        onStopSession = viewModel::onStopSession,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    paddingValues: PaddingValues,
    onToggleMicrophone: () -> Unit,
    onStopSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    if (state.isLoading) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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
        MicrophoneButton(isActive = state.isMicActive, onToggle = onToggleMicrophone)
        Divider()
        Text(text = "实时字幕", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
            items(state.transcriptHistory) { item ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(text = item.transcript, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Button(onClick = onOpenSettings) {
            Text(text = "打开设置")
        }
        Button(onClick = onOpenHistory) {
            Text(text = "查看历史")
        }
        Button(onClick = onStopSession) {
            Text(text = "结束会话")
        }
    }
}
