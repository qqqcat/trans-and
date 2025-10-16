package com.example.translatorapp.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MicrophoneButton(
    isActive: Boolean,
    onToggle: () -> Unit,
) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    FilledIconButton(
        onClick = onToggle,
        modifier = Modifier.size(72.dp),
        colors = ButtonDefaults.iconButtonColors(containerColor = containerColor)
    ) {
        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
    }
}
