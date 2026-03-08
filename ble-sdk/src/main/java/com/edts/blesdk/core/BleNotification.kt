package com.edts.blesdk.core

import java.util.UUID

data class BleNotification(
    val serviceUuid: UUID,
    val charUuid: UUID,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BleNotification

        if (serviceUuid != other.serviceUuid) return false
        if (charUuid != other.charUuid) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serviceUuid.hashCode()
        result = 31 * result + charUuid.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}