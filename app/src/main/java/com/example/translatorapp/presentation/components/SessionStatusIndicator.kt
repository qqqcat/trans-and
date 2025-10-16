package com.example.translatorapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.translatorapp.domain.model.LatencyMetrics

@Composable
fun SessionStatusIndicator(
    latencyMetrics: LatencyMetrics,
    isMicrophoneActive: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(text = if (isMicrophoneActive) "麦克风已开启" else "麦克风已关闭", style = MaterialTheme.typography.titleMedium)
                Text(text = "ASR ${latencyMetrics.asrLatencyMs}ms / 翻译 ${latencyMetrics.translationLatencyMs}ms / TTS ${latencyMetrics.ttsLatencyMs}ms",
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (errorMessage != null) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
