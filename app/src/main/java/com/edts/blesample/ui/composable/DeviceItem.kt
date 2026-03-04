package com.edts.blesample.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edts.blesample.ui.preview.CompletePreview
import com.edts.blesdk.model.BleDevice

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