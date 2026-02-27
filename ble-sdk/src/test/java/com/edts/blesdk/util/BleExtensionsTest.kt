package com.edts.blesdk.util

import android.bluetooth.BluetoothGattCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Test

class BleExtensionsTest {

    @Test
    fun parseHeartRate_emptyData_returnsZero() {
        val result = BleExtensions.parseHeartRate(ByteArray(0))
        assertEquals(0, result)
    }

    @Test
    fun parseHeartRate_uint8Format_returnsCorrectValue() {
        // Flag 0x00 indicates UINT8 format (1st bit is 0)
        // Value is 75 (0x4B)
        val data = byteArrayOf(0x00, 0x4B)
        val result = BleExtensions.parseHeartRate(data)
        assertEquals(75, result)
    }

    @Test
    fun parseHeartRate_uint16Format_returnsCorrectValue() {
        // Flag 0x01 indicates UINT16 format (1st bit is 1)
        // Value is 300 (0x012C -> little endian: 0x2C, 0x01)
        val data = byteArrayOf(0x01, 0x2C, 0x01)
        val result = BleExtensions.parseHeartRate(data)
        assertEquals(300, result)
    }

    @Test
    fun parseBatteryLevel_emptyData_returnsZero() {
        val result = BleExtensions.parseBatteryLevel(ByteArray(0))
        assertEquals(0, result)
    }

    @Test
    fun parseBatteryLevel_validData_returnsValue() {
        // Battery level 85%
        val data = byteArrayOf(85.toByte())
        val result = BleExtensions.parseBatteryLevel(data)
        assertEquals(85, result)
    }
}
