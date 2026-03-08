package com.sunstrinq.blesample.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sunstrinq.blesample.ui.preview.CompletePreview

@Composable
fun LogView(
    logs: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll effect
    LaunchedEffect(logs) {
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(modifier = modifier.verticalScroll(scrollState)) {
        Text(
            text = logs,
            color = Color.Green,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

// --- Previews ---

@CompletePreview
@Composable
fun LogViewPreview() {
    MaterialTheme {
        LogView(
            logs = "App started\nStarting scan...\nFound: Smart Watch A - AA:BB:CC\nConnected! Discovering services...\nServices discovered",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Black)
                .padding(8.dp)
        )
    }
}