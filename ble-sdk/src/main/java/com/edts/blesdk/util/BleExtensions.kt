package com.edts.blesdk.util

import android.bluetooth.BluetoothGattCharacteristic
import com.edts.blesdk.model.BloodPressureMeasurement
import com.edts.blesdk.model.TemperatureMeasurement
import com.edts.blesdk.model.WeightMeasurementData
import kotlin.math.pow

object BleExtensions {

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

    fun parseBloodPressure(data: ByteArray): BloodPressureMeasurement? {
        if (data.size < 7) return null
        val flags = data[0].toInt()
        val unitIsKpa = (flags and 0x01) != 0

        val sys = bytesToSFloat(data[1], data[2])
        val dia = bytesToSFloat(data[3], data[4])
        val map = bytesToSFloat(data[5], data[6])
        return BloodPressureMeasurement(sys, dia, map, unitIsKpa)
    }

    fun parseHealthThermometer(data: ByteArray): TemperatureMeasurement? {
        if (data.size < 5) return null
        val flags = data[0].toInt()
        val unitIsFahrenheit = (flags and 0x01) != 0
        val temp = bytesToFloat(data, 1)
        return TemperatureMeasurement(temp, unitIsFahrenheit)
    }

    fun parseWeightMeasurement(data: ByteArray): WeightMeasurementData? {
        if (data.size < 3) return null
        val flags = data[0].toInt()
        val unitIsLbs = (flags and 0x01) != 0

        val weightInt = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val resolution = if (unitIsLbs) 0.01f else 0.005f
        return WeightMeasurementData(weightInt * resolution, unitIsLbs)
    }
}
