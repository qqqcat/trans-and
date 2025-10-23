package com.example.translatorapp.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "WebRTC 实时翻译 Demo", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        when (uiState.session.status) {
            SessionStatus.Idle -> {
                Button(onClick = {
                    val model = uiState.session.model.realtimeModel ?: "gpt-realtime-mini"
                    viewModel.startWebRtcSession(model)
                }) {
                    Text("启动 WebRTC 会话")
                }
            }
            SessionStatus.Streaming -> {
                Row {
                    Button(onClick = { viewModel.startWebRtcRecording() }) {
                        Text("开始录音")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { viewModel.stopWebRtcSession() }) {
                        Text("断开会话")
                    }
                }
            }
            else -> {
                Text("状态: ${uiState.session.status}")
            }
        }
        if (uiState.session.isMicrophoneOpen) {
            Button(onClick = { viewModel.stopWebRtcRecording() }, modifier = Modifier.padding(top = 16.dp)) {
                Text("停止录音")
            }
        }
        if (uiState.session.lastErrorMessage != null) {
            Text(text = "错误: ${uiState.session.lastErrorMessage}", color = MaterialTheme.colorScheme.error)
        }
    }
}
