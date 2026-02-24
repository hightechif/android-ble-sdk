package com.edts.blesdk.model

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val name: String?,
    val macAddress: String,
    val rssi: Int,
    val device: BluetoothDevice? // Keep reference to internal device for connection
)
