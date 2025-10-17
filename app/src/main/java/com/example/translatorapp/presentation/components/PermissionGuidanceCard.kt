@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.translatorapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.translatorapp.R

@Composable
fun PermissionGuidanceCard(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(id = R.string.permission_guidance_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.permission_guidance_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.permission_guidance_step_request),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(id = R.string.permission_guidance_step_settings),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.permission_guidance_request))
                }
                TextButton(
                    onClick = onOpenPermissionSettings,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(id = R.string.permission_guidance_open_settings)
                    )
                }
            }
        }
    }
}
