package com.edts.blesample.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edts.blesample.ui.composable.ControlPanel
import com.edts.blesample.ui.composable.DeviceList
import com.edts.blesample.ui.composable.LogView
import com.edts.blesample.ui.preview.DarkModePreview
import com.edts.blesample.ui.preview.StandardPreview
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
    onSubscribeHeartRateClick: () -> Unit,
    onSubscribeBloodPressureClick: () -> Unit,
    onSubscribeThermometerClick: () -> Unit,
    onSubscribeWeightScaleClick: () -> Unit,
    onReadRssiClick: () -> Unit,
    onWriteDummyClick: () -> Unit,
    onDisableHeartRateClick: () -> Unit
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.LightGray.copy(alpha = 0.2f))
        )

        Spacer(modifier = Modifier.height(16.dp))
        ControlPanel(
            isConnected = isConnected,
            onSubscribeHeartRateClick = onSubscribeHeartRateClick,
            onSubscribeBloodPressureClick = onSubscribeBloodPressureClick,
            onSubscribeThermometerClick = onSubscribeThermometerClick,
            onSubscribeWeightScaleClick = onSubscribeWeightScaleClick,
            onReadRssiClick = onReadRssiClick,
            onWriteDummyClick = onWriteDummyClick,
            onDisableHeartRateClick = onDisableHeartRateClick,
            modifier = Modifier
                .fillMaxWidth()
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

// --- Previews ---

@StandardPreview
@DarkModePreview
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
            onSubscribeHeartRateClick = {},
            onSubscribeBloodPressureClick = {},
            onSubscribeThermometerClick = {},
            onSubscribeWeightScaleClick = {},
            onReadRssiClick = {},
            onWriteDummyClick = {},
            onDisableHeartRateClick = {}
        )
    }
}
