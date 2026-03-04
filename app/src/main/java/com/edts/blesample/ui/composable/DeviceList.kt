package com.edts.blesample.ui.composable

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.edts.blesample.ui.preview.CompletePreview
import com.edts.blesdk.model.BleDevice

@Composable
fun DeviceList(
    devices: List<BleDevice>,
    connectedDevice: BleDevice?,
    onDeviceConnect: (BleDevice) -> Unit,
    onDeviceDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(
            items = devices,
            key = { _, item -> item.macAddress }
        ) { _, device ->
            val isCurrentDeviceConnected = connectedDevice?.macAddress == device.macAddress

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

// --- Previews ---

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
            onDeviceDisconnect = {}
        )
    }
}