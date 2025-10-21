package com.example.translatorapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.translatorapp.R
import com.example.translatorapp.domain.model.LatencyMetrics
import com.example.translatorapp.domain.model.SessionInitializationStatus
import com.example.translatorapp.presentation.theme.LocalSpacing
import kotlin.math.roundToInt

@Composable
fun SessionStatusIndicator(
    latencyMetrics: LatencyMetrics,
    initializationStatus: SessionInitializationStatus,
    initializationProgress: Float,
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
        when (initializationStatus) {
            SessionInitializationStatus.Downloading -> {
                LinearProgressIndicator(
                    progress = { initializationProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SessionInitializationStatus.Preparing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            else -> Unit
        }
        val initializationText = when (initializationStatus) {
            SessionInitializationStatus.Downloading -> stringResource(
                id = R.string.home_initialization_downloading,
                (initializationProgress.coerceIn(0f, 1f) * 100f).roundToInt()
            )
            SessionInitializationStatus.Preparing -> stringResource(id = R.string.home_initialization_preparing)
            SessionInitializationStatus.Ready -> stringResource(id = R.string.home_initialization_ready)
            SessionInitializationStatus.Idle -> null
        }
        if (!initializationText.isNullOrBlank()) {
            Text(
                text = initializationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
