package com.example.translatorapp.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.translatorapp.domain.model.TranslationHistoryItem
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    paddingValues: PaddingValues,
    onBack: () -> Unit
) {
    val history by viewModel.history.collectAsState()
    HistoryScreen(
        history = history,
        paddingValues = paddingValues,
        onBack = onBack,
        onClear = viewModel::clearHistory
    )
}

@Composable
fun HistoryScreen(
    history: List<TranslationHistoryItem>,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(history) { item ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(text = item.sourceText, style = MaterialTheme.typography.bodyLarge)
                    Text(text = item.translatedText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = item.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Divider()
            }
        }
        Button(onClick = onClear) {
            Text(text = "清空历史")
        }
        Button(onClick = onBack) {
            Text(text = "返回")
        }
    }
}
