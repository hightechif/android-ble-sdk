package com.edts.blesample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edts.blesdk.model.BleDevice

@Composable
fun ScanScreen(
    scannedDevices: List<BleDevice>,
    isConnected: Boolean,
    logs: String,
    onScanClick: () -> Unit,
    onConnectClick: (BleDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        ) {
            Text(if (isConnected) "Connected" else "Scan")
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isConnected) "Status: Connected" else "Status: Disconnected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected) Color.Green else Color.Red
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Devices:",
            style = MaterialTheme.typography.titleMedium
        )
        
        DeviceList(
            devices = scannedDevices,
            onDeviceClick = onConnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.LightGray.copy(alpha = 0.2f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Logs:",
            style = MaterialTheme.typography.titleMedium
        )
        
        LogView(
            logs = logs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(8.dp)
        )
    }
}

@Composable
fun DeviceList(
    devices: List<BleDevice>,
    onDeviceClick: (BleDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(devices) { device ->
            DeviceItem(device = device, onClick = { onDeviceClick(device) })
        }
    }
}

@Composable
fun DeviceItem(
    device: BleDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = device.name ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = device.macAddress,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "RSSI: ${device.rssi}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun LogView(
    logs: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll effect could be added here with LaunchedEffect
    
    Box(modifier = modifier.verticalScroll(scrollState)) {
        Text(
            text = logs,
            color = Color.Green,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
