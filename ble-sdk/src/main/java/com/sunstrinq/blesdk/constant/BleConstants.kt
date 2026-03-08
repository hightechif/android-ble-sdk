package com.sunstrinq.blesdk.constant

import java.util.UUID

object BleConstants {
    /**
     * Client Characteristic Configuration Descriptor (CCCD) UUID.
     * Required for enabling/disabling notifications and indications.
     */
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

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
}
