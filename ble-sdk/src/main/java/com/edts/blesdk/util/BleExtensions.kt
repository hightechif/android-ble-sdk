package com.edts.blesdk.util

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

object BleExtensions {

    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    fun parseHeartRate(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        
        val flag = data[0].toInt()
        val format = if ((flag and 0x01) != 0) {
            BluetoothGattCharacteristic.FORMAT_UINT16
        } else {
            BluetoothGattCharacteristic.FORMAT_UINT8
        }
        
        // This is a simplified parser assuming standard format
        // Ideally we'd wrap data in a Characteristic to use getIntValue, but we can't easily instantiate it without a gatt instance.
        // Manual bit manipulation:

        return if (format == BluetoothGattCharacteristic.FORMAT_UINT8) {
            data[1].toInt() and 0xFF
        } else {
            // UINT16, Little Endian
            ((data[2].toInt() and 0xFF) shl 8) + (data[1].toInt() and 0xFF)
        }
    }
    
    fun parseBatteryLevel(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        return data[0].toInt() and 0xFF
    }
}
