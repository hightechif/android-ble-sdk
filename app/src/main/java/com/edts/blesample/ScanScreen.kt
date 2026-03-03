package com.edts.blesample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edts.blesdk.model.BleDevice

@Composable
fun ScanScreen(
    scannedDevices: List<BleDevice>,
    isConnected: Boolean,
    isScanning: Boolean,
    filterUnknown: Boolean,
    connectedDevice: BleDevice?,
    logs: String,
    onFilterUnknownChange: (Boolean) -> Unit,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onConnectClick: (BleDevice) -> Unit,
    onDisconnectClick: () -> Unit,
    onReadNotificationClick: () -> Unit,
    onWriteMessageClick: () -> Unit,
    onDisableNotificationClick: () -> Unit,
    onReadRssiClick: () -> Unit,
    onLoadMoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val buttonText = when {
            isConnected -> "Connected"
            isScanning -> "Stop Scan"
            else -> "Scan"
        }

        Button(
            onClick = {
                if (isScanning) onStopScanClick() else onScanClick()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected,
            colors = if (isScanning && !isConnected) ButtonDefaults.buttonColors(
                containerColor = Color.Red,
                contentColor = Color.White
            ) else ButtonDefaults.buttonColors()
        ) {
            Text(buttonText)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isConnected) "Status: Connected" else "Status: Disconnected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected) Color.Green else Color.Red
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Devices:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Filter Unknown:",
                style = MaterialTheme.typography.bodyMedium
            )
            Checkbox(
                checked = filterUnknown,
                onCheckedChange = onFilterUnknownChange
            )
        }

        DeviceList(
            devices = scannedDevices,
            connectedDevice = connectedDevice,
            onDeviceConnect = onConnectClick,
            onDeviceDisconnect = onDisconnectClick,
            onLoadMore = onLoadMoreClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.LightGray.copy(alpha = 0.2f))
        )

        Spacer(modifier = Modifier.height(16.dp))
        ControlPanel(
            isConnected = isConnected,
            onReadNotificationClick = onReadNotificationClick,
            onWriteMessageClick = onWriteMessageClick,
            onDisableNotificationClick = onDisableNotificationClick,
            onReadRssiClick = onReadRssiClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(8.dp)
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
    connectedDevice: BleDevice?,
    onDeviceConnect: (BleDevice) -> Unit,
    onDeviceDisconnect: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(
            items = devices,
            key = { _, item -> item.macAddress }
        ) { index, device ->
            val isCurrentDeviceConnected = connectedDevice?.macAddress == device.macAddress

            if (index == devices.lastIndex && devices.size >= 10) {
                LaunchedEffect(device.macAddress) {
                    onLoadMore()
                }
            }

            DeviceItem(
                device = device,
                isCurrentDeviceConnected = isCurrentDeviceConnected,
                onClick = { },
                onConnect = { onDeviceConnect(device) },
                onDisconnect = onDeviceDisconnect
            )
        }
    }
}

@Composable
fun DeviceItem(
    device: BleDevice,
    isCurrentDeviceConnected: Boolean,
    onClick: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
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
            Button(
                modifier = Modifier.padding(12.dp),
                onClick = if (isCurrentDeviceConnected) onDisconnect else onConnect,
                colors = if (isCurrentDeviceConnected) ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                ) else ButtonDefaults.buttonColors()
            ) {
                Text(if (isCurrentDeviceConnected) "Disconnect" else "Connect")
            }
        }
    }
}

@Composable
fun ControlPanel(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    onReadNotificationClick: () -> Unit,
    onWriteMessageClick: () -> Unit,
    onDisableNotificationClick: () -> Unit,
    onReadRssiClick: () -> Unit
) {
    Box(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Actions:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                    contentDescription = "Control Panel",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.padding(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onReadNotificationClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF005073),
                        contentColor = Color(0xFF03A9F4),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Read Notification", fontSize = 12.sp)
                }
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onWriteMessageClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF005073),
                        contentColor = Color(0xFF03A9F4),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Write Message", fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onDisableNotificationClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF800020),
                        contentColor = Color(0xFFCE2029),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Disable Notification", fontSize = 12.sp)
                }
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onReadRssiClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF005073),
                        contentColor = Color(0xFF03A9F4),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Read RSSI", fontSize = 12.sp)
                }
            }
        }
    }
}

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
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

// --- Previews ---

@CompletePreview
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
            isCurrentDeviceConnected = false,
            onClick = {},
            onConnect = {},
            onDisconnect = {}
        )
    }
}

@CompletePreview
@Composable
fun DeviceListPreview() {
    MaterialTheme {
        val mockData = listOf(
            BleDevice("Smart Watch A", "AA:BB:CC:DD:EE:FF", -50, null),
            BleDevice("Smart Tracker B", "11:22:33:44:55:66", -70, null),
            BleDevice("Unknown Device", "FF:EE:DD:CC:BB:AA", -90, null)
        )
        DeviceList(
            devices = mockData,
            connectedDevice = null,
            onDeviceConnect = {},
            onDeviceDisconnect = {},
            onLoadMore = {})
    }
}

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

@CompletePreview
@Composable
fun ScanScreenPreview() {
    MaterialTheme {
        val mockData = listOf(
            BleDevice("Smart Watch A", "AA:BB:CC:DD:EE:FF", -50, null),
            BleDevice("Smart Tracker B", "11:22:33:44:55:66", -70, null)
        )
        ScanScreen(
            scannedDevices = mockData,
            isConnected = true,
            isScanning = false,
            filterUnknown = false,
            connectedDevice = mockData[0],
            logs = "App started\nStarting scan...\nFound: Smart Watch A\nFound: Smart Tracker B",
            onFilterUnknownChange = {},
            onScanClick = {},
            onStopScanClick = {},
            onConnectClick = {},
            onDisconnectClick = {},
            onReadNotificationClick = {},
            onWriteMessageClick = {},
            onDisableNotificationClick = {},
            onReadRssiClick = {},
            onLoadMoreClick = {}
        )
    }
}
