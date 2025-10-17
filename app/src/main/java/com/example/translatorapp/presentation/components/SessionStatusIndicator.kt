package com.example.translatorapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.LatencyMetrics
import com.example.translatorapp.presentation.theme.LocalSpacing

@Composable
fun SessionStatusIndicator(
    latencyMetrics: LatencyMetrics,
    isMicrophoneActive: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            text = if (isMicrophoneActive) stringResource(R.string.home_status_indicator_on) else stringResource(R.string.home_status_indicator_off),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isMicrophoneActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                id = R.string.home_latency_label,
                latencyMetrics.asrLatencyMs,
                latencyMetrics.translationLatencyMs,
                latencyMetrics.ttsLatencyMs
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
