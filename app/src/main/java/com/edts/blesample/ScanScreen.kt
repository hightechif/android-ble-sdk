package com.edts.blesample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
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
        items(
            items = devices,
            key = { it.macAddress }
        ) { device ->
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

// --- Previews ---

@Preview(showBackground = true)
@Composable
fun DeviceItemPreview() {
    MaterialTheme {
        DeviceItem(
            device = BleDevice(
                name = "Test Wristband",
                macAddress = "00:11:22:33:44:55",
                rssi = -65,
                device = null // Mock null BluetoothDevice to avoid context crash
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceListPreview() {
    MaterialTheme {
        val mockData = listOf(
            BleDevice("Smart Watch A", "AA:BB:CC:DD:EE:FF", -50, null),
            BleDevice("Smart Tracker B", "11:22:33:44:55:66", -70, null),
            BleDevice("Unknown Device", "FF:EE:DD:CC:BB:AA", -90, null)
        )
        DeviceList(devices = mockData, onDeviceClick = {})
    }
}

@Preview(showBackground = true)
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ScanScreenPreview() {
    MaterialTheme {
        val mockData = listOf(
            BleDevice("Smart Watch A", "AA:BB:CC:DD:EE:FF", -50, null),
            BleDevice("Smart Tracker B", "11:22:33:44:55:66", -70, null)
        )
        ScanScreen(
            scannedDevices = mockData,
            isConnected = false,
            logs = "App started\nStarting scan...\nFound: Smart Watch A\nFound: Smart Tracker B",
            onScanClick = {},
            onConnectClick = {}
        )
    }
}
