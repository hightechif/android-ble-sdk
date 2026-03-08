package com.edts.blesdk.model

import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

// Sealed classes for operations
sealed class BleOperation {
    val completion = CompletableDeferred<BleResult>()

    class DiscoverServices : BleOperation()
    data class RequestMtu(val mtu: Int) : BleOperation()
    data class ReadCharacteristic(val serviceUuid: UUID, val charUuid: UUID) : BleOperation()
    data class WriteCharacteristic(
        val serviceUuid: UUID,
        val charUuid: UUID,
        val value: ByteArray
    ) : BleOperation() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as WriteCharacteristic

            if (serviceUuid != other.serviceUuid) return false
            if (charUuid != other.charUuid) return false
            if (!value.contentEquals(other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = serviceUuid.hashCode()
            result = 31 * result + charUuid.hashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }

    data class EnableNotifications(val serviceUuid: UUID, val charUuid: UUID) : BleOperation()
    data class DisableNotifications(val serviceUuid: UUID, val charUuid: UUID) : BleOperation()
    class ReadRssi : BleOperation()
}
