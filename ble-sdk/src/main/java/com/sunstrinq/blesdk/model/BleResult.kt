package com.sunstrinq.blesdk.model

sealed class BleResult {
    data class Success(val data: ByteArray?) : BleResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false

            return true
        }

        override fun hashCode(): Int {
            return data?.contentHashCode() ?: 0
        }
    }

    data class SuccessRssi(val rssi: Int) : BleResult()

    data class Error(val message: String) : BleResult()
}
