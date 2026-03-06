package com.edts.blesdk.util

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID
import kotlin.math.pow

object BleExtensions {

    private const val BASE_UUID_FORMAT = "0000%04x-0000-1000-8000-00805f9b34fb"

    /**
     * Generates a standard Bluetooth SIG UUID from a 16-bit short UUID.
     */
    fun getSigUuid(shortUuid16: Int): UUID {
        return UUID.fromString(String.format(BASE_UUID_FORMAT, shortUuid16))
    }

    // Standard Services
    val HEART_RATE_SERVICE_UUID = getSigUuid(0x180D)
    val BATTERY_SERVICE_UUID = getSigUuid(0x180F)
    val BLOOD_PRESSURE_SERVICE_UUID = getSigUuid(0x1810)
    val HEALTH_THERMOMETER_SERVICE_UUID = getSigUuid(0x1809)
    val DEVICE_INFORMATION_SERVICE_UUID = getSigUuid(0x180A)
    val WEIGHT_SCALE_SERVICE_UUID = getSigUuid(0x181D)
    val GLUCOSE_SERVICE_UUID = getSigUuid(0x1808)
    val CYCLING_SPEED_CADENCE_SERVICE_UUID = getSigUuid(0x1816)
    val RUNNING_SPEED_CADENCE_SERVICE_UUID = getSigUuid(0x1814)
    val ENVIRONMENTAL_SENSING_SERVICE_UUID = getSigUuid(0x181A)
    val BODY_COMPOSITION_SERVICE_UUID = getSigUuid(0x181B)
    val CURRENT_TIME_SERVICE_UUID = getSigUuid(0x1805)

    // Standard Characteristics
    val HEART_RATE_MEASUREMENT_CHAR_UUID = getSigUuid(0x2A37)
    val BATTERY_LEVEL_CHAR_UUID = getSigUuid(0x2A19)
    val BLOOD_PRESSURE_MEASUREMENT_CHAR_UUID = getSigUuid(0x2A35)
    val TEMPERATURE_MEASUREMENT_CHAR_UUID = getSigUuid(0x2A1C)
    val WEIGHT_MEASUREMENT_CHAR_UUID = getSigUuid(0x2A9D)
    val GLUCOSE_MEASUREMENT_CHAR_UUID = getSigUuid(0x2A18)
    val CURRENT_TIME_CHAR_UUID = getSigUuid(0x2A2B)

    // Device Information Characteristics
    val SYSTEM_ID_CHAR_UUID = getSigUuid(0x2A23)
    val MODEL_NUMBER_STRING_CHAR_UUID = getSigUuid(0x2A24)
    val SERIAL_NUMBER_STRING_CHAR_UUID = getSigUuid(0x2A25)
    val FIRMWARE_REVISION_STRING_CHAR_UUID = getSigUuid(0x2A26)
    val HARDWARE_REVISION_STRING_CHAR_UUID = getSigUuid(0x2A27)
    val SOFTWARE_REVISION_STRING_CHAR_UUID = getSigUuid(0x2A28)
    val MANUFACTURER_NAME_STRING_CHAR_UUID = getSigUuid(0x2A29)

    // --- Parsing Functions (conforming to Bluetooth SIG specs) ---

    fun parseHeartRate(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val flag = data[0].toInt()
        val format = if ((flag and 0x01) != 0) {
            BluetoothGattCharacteristic.FORMAT_UINT16
        } else {
            BluetoothGattCharacteristic.FORMAT_UINT8
        }
        return if (format == BluetoothGattCharacteristic.FORMAT_UINT8) {
            if (data.size < 2) return 0
            data[1].toInt() and 0xFF
        } else {
            if (data.size < 3) return 0
            ((data[2].toInt() and 0xFF) shl 8) + (data[1].toInt() and 0xFF)
        }
    }

    fun parseBatteryLevel(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        return data[0].toInt() and 0xFF
    }

    /**
     * Parse Device Information String (UTF-8)
     */
    fun parseString(data: ByteArray): String {
        return String(data, Charsets.UTF_8)
    }

    /**
     * Parses IEEE-11073 32-bit FLOAT from the given offset
     */
    private fun bytesToFloat(data: ByteArray, offset: Int): Float {
        if (data.size < offset + 4) return 0f
        val mantissaStr = ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                (data[offset].toInt() and 0xFF)
        val exponent = data[offset + 3].toInt()

        // Convert to signed 24-bit
        val mantissa = if ((mantissaStr and 0x00800000) != 0) {
            mantissaStr or -0x1000000
        } else {
            mantissaStr
        }
        return (mantissa * 10.0.pow(exponent.toDouble())).toFloat()
    }

    /**
     * Parses IEEE-11073 16-bit SFLOAT from two bytes
     */
    private fun bytesToSFloat(b1: Byte, b2: Byte): Float {
        val mantissaStr = (b1.toInt() and 0xFF) or ((b2.toInt() and 0x0F) shl 8)
        val exponent = (b2.toInt() shr 4)

        val signedMantissa = if ((mantissaStr and 0x0800) != 0) {
            mantissaStr or -0x1000
        } else {
            mantissaStr
        }
        val signedExponent = if ((exponent and 0x08) != 0) {
            exponent or -0x10
        } else {
            exponent
        }
        return (signedMantissa * 10.0.pow(signedExponent.toDouble())).toFloat()
    }

    data class BloodPressureMeasurement(
        val systolic: Float,
        val diastolic: Float,
        val map: Float,
        val unitIsKpa: Boolean
    )

    fun parseBloodPressure(data: ByteArray): BloodPressureMeasurement? {
        if (data.size < 7) return null
        val flags = data[0].toInt()
        val unitIsKpa = (flags and 0x01) != 0

        val sys = bytesToSFloat(data[1], data[2])
        val dia = bytesToSFloat(data[3], data[4])
        val map = bytesToSFloat(data[5], data[6])
        return BloodPressureMeasurement(sys, dia, map, unitIsKpa)
    }

    data class TemperatureMeasurement(val temperature: Float, val unitIsFahrenheit: Boolean)

    fun parseHealthThermometer(data: ByteArray): TemperatureMeasurement? {
        if (data.size < 5) return null
        val flags = data[0].toInt()
        val unitIsFahrenheit = (flags and 0x01) != 0
        val temp = bytesToFloat(data, 1)
        return TemperatureMeasurement(temp, unitIsFahrenheit)
    }

    data class WeightMeasurementData(val weight: Float, val unitIsLbs: Boolean)

    fun parseWeightMeasurement(data: ByteArray): WeightMeasurementData? {
        if (data.size < 3) return null
        val flags = data[0].toInt()
        val unitIsLbs = (flags and 0x01) != 0

        val weightInt = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val resolution = if (unitIsLbs) 0.01f else 0.005f
        return WeightMeasurementData(weightInt * resolution, unitIsLbs)
    }
}
