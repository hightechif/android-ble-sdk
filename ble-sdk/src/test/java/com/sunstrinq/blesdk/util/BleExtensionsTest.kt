package com.sunstrinq.blesdk.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import com.sunstrinq.blesdk.constant.BleConstants

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
    fun `HEART_RATE_SERVICE_UUID is correct`() {
        val uuid = BleConstants.HEART_RATE_SERVICE_UUID
        assertThat(uuid.toString()).isEqualTo("0000180d-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `HEART_RATE_MEASUREMENT_CHAR_UUID is correct`() {
        val uuid = BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID
        assertThat(uuid.toString()).isEqualTo("00002a37-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `BATTERY_SERVICE_UUID is correct`() {
        val uuid = BleConstants.BATTERY_SERVICE_UUID
        assertThat(uuid.toString()).isEqualTo("0000180f-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `BATTERY_LEVEL_CHAR_UUID is correct`() {
        val uuid = BleConstants.BATTERY_LEVEL_CHAR_UUID
        assertThat(uuid.toString()).isEqualTo("00002a19-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `getSigUuid generates correct UUID given short UUID`() {
        val uuid = BleConstants.getSigUuid(0x180D)
        assertThat(uuid.toString()).isEqualTo("0000180d-0000-1000-8000-00805f9b34fb")
    }

    // ─── parseBloodPressure ───────────────────────────────────────────────────────
    
    @Test
    fun `parseBloodPressure returns null when data length is less than 7`() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5)
        val result = BleExtensions.parseBloodPressure(data)
        assertThat(result).isNull()
    }

    @Test
    fun `parseBloodPressure returns correct measurement`() {
        // Mocking a blood pressure packet: Length = 7 bytes
        // Flags (1 byte): 0x00 (mmHg) -> unitIsKpa = false
        // Systolic (2 bytes, SFLOAT): 120 -> 0x0078
        // Diastolic (2 bytes, SFLOAT): 80 -> 0x0050
        // MAP (2 bytes, SFLOAT): 100 -> 0x0064
        val data = byteArrayOf(0x00, 0x78, 0x00, 0x50, 0x00, 0x64, 0x00)
        
        val result = BleExtensions.parseBloodPressure(data)
        
        assertThat(result).isNotNull()
        assertThat(result?.unitIsKpa).isFalse()
        assertThat(result?.systolic).isEqualTo(120f)
        assertThat(result?.diastolic).isEqualTo(80f)
        assertThat(result?.map).isEqualTo(100f)
    }

    // ─── parseHealthThermometer ───────────────────────────────────────────────────

    @Test
    fun `parseHealthThermometer returns null when data length is less than 5`() {
        val data = byteArrayOf(0, 1, 2, 3)
        val result = BleExtensions.parseHealthThermometer(data)
        assertThat(result).isNull()
    }

    @Test
    fun `parseHealthThermometer returns correct measurement`() {
        // Flags (1 byte): 0x00 (Celsius)
        // Temp Value (4 bytes, FLOAT): 36.5 = 365 * 10^-1 -> Mantissa: 0x00016D (365), Exponent: 0xFF (-1)
        val data = byteArrayOf(0x00, 0x6D, 0x01, 0x00, 0xFF.toByte())

        val result = BleExtensions.parseHealthThermometer(data)

        assertThat(result).isNotNull()
        assertThat(result?.unitIsFahrenheit).isFalse()
        assertThat(result?.temperature).isEqualTo(36.5f)
    }

    // ─── parseWeightMeasurement ───────────────────────────────────────────────────

    @Test
    fun `parseWeightMeasurement returns null when data length is less than 3`() {
        val data = byteArrayOf(0, 1)
        val result = BleExtensions.parseWeightMeasurement(data)
        assertThat(result).isNull()
    }

    @Test
    fun `parseWeightMeasurement returns correct measurement`() {
        // Flags (1 byte): 0x00 (SI / kg)
        // Weight (2 bytes, UINT16): 70.0 kg -> resolution is 0.005 -> 70.0 / 0.005 = 14000 -> 0x36B0
        val data = byteArrayOf(0x00, 0xB0.toByte(), 0x36)

        val result = BleExtensions.parseWeightMeasurement(data)

        assertThat(result).isNotNull()
        assertThat(result?.unitIsLbs).isFalse()
        assertThat(result?.weight).isEqualTo(70.0f)
    }
}
