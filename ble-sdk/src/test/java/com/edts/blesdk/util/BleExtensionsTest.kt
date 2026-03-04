package com.edts.blesdk.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BleExtensionsTest {

    // ─── parseHeartRate ───────────────────────────────────────────────────────────

    @Test
    fun `parseHeartRate returns zero when data is empty`() {
        // Arrange
        val data = ByteArray(0)

        // Act
        val result = BleExtensions.parseHeartRate(data)

        // Assert
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `parseHeartRate returns correct value when format is UINT8`() {
        // Arrange
        // Flag 0x00 → bit 0 is 0 → UINT8 format. Value byte = 0x4B = 75.
        val data = byteArrayOf(0x00, 0x4B)

        // Act
        val result = BleExtensions.parseHeartRate(data)

        // Assert
        assertThat(result).isEqualTo(75)
    }

    @Test
    fun `parseHeartRate returns correct value when format is UINT16`() {
        // Arrange
        // Flag 0x01 → bit 0 is 1 → UINT16 format. Value = 300 little-endian: 0x2C, 0x01.
        val data = byteArrayOf(0x01, 0x2C, 0x01)

        // Act
        val result = BleExtensions.parseHeartRate(data)

        // Assert
        assertThat(result).isEqualTo(300)
    }

    @Test
    fun `parseHeartRate returns max UINT8 value when byte is 0xFF`() {
        // Arrange
        val data = byteArrayOf(0x00, 0xFF.toByte())

        // Act
        val result = BleExtensions.parseHeartRate(data)

        // Assert
        assertThat(result).isEqualTo(255)
    }

    @Test
    fun `parseHeartRate returns minimum valid heart rate when value is one`() {
        // Arrange
        val data = byteArrayOf(0x00, 0x01)

        // Act
        val result = BleExtensions.parseHeartRate(data)

        // Assert
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `parseHeartRate returns zero heart rate when UINT8 value byte is zero`() {
        // Arrange
        val data = byteArrayOf(0x00, 0x00)

        // Act
        val result = BleExtensions.parseHeartRate(data)

        // Assert
        assertThat(result).isEqualTo(0)
    }

    // ─── parseBatteryLevel ────────────────────────────────────────────────────────

    @Test
    fun `parseBatteryLevel returns zero when data is empty`() {
        // Arrange
        val data = ByteArray(0)

        // Act
        val result = BleExtensions.parseBatteryLevel(data)

        // Assert
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `parseBatteryLevel returns correct value when data has valid battery byte`() {
        // Arrange
        val data = byteArrayOf(85.toByte())

        // Act
        val result = BleExtensions.parseBatteryLevel(data)

        // Assert
        assertThat(result).isEqualTo(85)
    }

    @Test
    fun `parseBatteryLevel returns one hundred when battery is full`() {
        // Arrange
        val data = byteArrayOf(100.toByte())

        // Act
        val result = BleExtensions.parseBatteryLevel(data)

        // Assert
        assertThat(result).isEqualTo(100)
    }

    @Test
    fun `parseBatteryLevel returns zero when battery is depleted`() {
        // Arrange
        val data = byteArrayOf(0x00)

        // Act
        val result = BleExtensions.parseBatteryLevel(data)

        // Assert
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `parseBatteryLevel returns 255 when byte value is 0xFF`() {
        // Arrange
        val data = byteArrayOf(0xFF.toByte())

        // Act
        val result = BleExtensions.parseBatteryLevel(data)

        // Assert
        assertThat(result).isEqualTo(255)
    }

    // ─── UUID constants ───────────────────────────────────────────────────────────

    @Test
    fun `HEART_RATE_SERVICE_UUID returns correct string representation when accessed`() {
        // Arrange — constant under test

        // Act
        val uuid = BleExtensions.HEART_RATE_SERVICE_UUID

        // Assert
        assertThat(uuid.toString()).isEqualTo("0000180d-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `HEART_RATE_MEASUREMENT_CHAR_UUID returns correct string representation when accessed`() {
        // Arrange — constant under test

        // Act
        val uuid = BleExtensions.HEART_RATE_MEASUREMENT_CHAR_UUID

        // Assert
        assertThat(uuid.toString()).isEqualTo("00002a37-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `BATTERY_SERVICE_UUID returns correct string representation when accessed`() {
        // Arrange — constant under test

        // Act
        val uuid = BleExtensions.BATTERY_SERVICE_UUID

        // Assert
        assertThat(uuid.toString()).isEqualTo("0000180f-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `BATTERY_LEVEL_CHAR_UUID returns correct string representation when accessed`() {
        // Arrange — constant under test

        // Act
        val uuid = BleExtensions.BATTERY_LEVEL_CHAR_UUID

        // Assert
        assertThat(uuid.toString()).isEqualTo("00002a19-0000-1000-8000-00805f9b34fb")
    }
}
