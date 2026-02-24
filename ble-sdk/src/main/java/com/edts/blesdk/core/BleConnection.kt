package com.edts.blesdk.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

class BleConnection(
    context: Context,
    private val bluetoothDevice: BluetoothDevice
) {

    private val appContext = context.applicationContext
    private var bluetoothGatt: BluetoothGatt? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Operation Queue to serialize GATT operations
    private val operationQueue = Channel<BleOperation>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pendingOperation: BleOperation? = null
    private val operationLock = Mutex()

    private val _notifications = Channel<BleNotification>(Channel.BUFFERED)
    val notifications: Flow<BleNotification> = _notifications.receiveAsFlow()

    init {
        scope.launch {
            for (operation in operationQueue) {
                // Wait until we can execute the next operation
                operationLock.withLock {
                    pendingOperation = operation
                    executeOperation(operation)
                    // The lock is released, but we need to wait for the callback to verify completion
                    // Typically we'd wait on a signal here, but for simplicity in this pass,
                    // we assume 'pendingOperation' logic handles the 'next' signal, or we rely on the lock being strictly around the 'start'
                    // Actually, a better pattern is to have the callback unlock a Mutex or signal completion.
                    // For now, let's use the blocking "execute" which will return only after callback triggers? 
                    // No, implementation below is async.

                    // 1. take item from channel (inside this loop)
                    // 2. execute
                    // 3. suspend until callback signals 'done'
                    // We need a way to suspend here with a timeout to prevent deadlocks.
                    val result = withTimeoutOrNull(5000) {
                        operation.completion.await()
                    }
                    if (result == null) {
                        operation.completion.complete(BleResult.Error("Operation timed out"))
                    }
                    pendingOperation = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING || _connectionState.value == ConnectionState.CONNECTED) return
        _connectionState.value = ConnectionState.CONNECTING
        bluetoothGatt = bluetoothDevice.connectGatt(appContext, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        _notifications.close()
    }

    suspend fun discoverServices(): Boolean {
        enqueueOperation(BleOperation.DiscoverServices)
        return true
    }

    suspend fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): ByteArray? {
        val op = BleOperation.ReadCharacteristic(serviceUuid, charUuid)
        enqueueOperation(op)
        // Wait for result from the operation
        return (op.completion.await() as? BleResult.Success)?.data
    }

    suspend fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, value: ByteArray) {
        val op = BleOperation.WriteCharacteristic(serviceUuid, charUuid, value)
        enqueueOperation(op)
        op.completion.await()
    }

    suspend fun enableNotifications(serviceUuid: UUID, charUuid: UUID) {
        val op = BleOperation.EnableNotifications(serviceUuid, charUuid)
        enqueueOperation(op)
        op.completion.await()
    }

    private suspend fun enqueueOperation(operation: BleOperation) {
        operationQueue.send(operation)
    }

    @SuppressLint("MissingPermission")
    private fun executeOperation(operation: BleOperation) {
        val gatt = bluetoothGatt ?: run {
            operation.completion.complete(BleResult.Error("Not connected"))
            return
        }

        when (operation) {
            is BleOperation.DiscoverServices -> {
                if (!gatt.discoverServices()) {
                    operation.completion.complete(BleResult.Error("Failed to start service discovery"))
                }
            }

            is BleOperation.ReadCharacteristic -> {
                val characteristic =
                    getCharacteristic(gatt, operation.serviceUuid, operation.charUuid)
                if (characteristic != null) {
                    if (!gatt.readCharacteristic(characteristic)) {
                        operation.completion.complete(BleResult.Error("Failed to start read"))
                    }
                } else {
                    operation.completion.complete(BleResult.Error("Characteristic not found"))
                }
            }

            is BleOperation.WriteCharacteristic -> {
                val characteristic =
                    getCharacteristic(gatt, operation.serviceUuid, operation.charUuid)
                if (characteristic != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val result = gatt.writeCharacteristic(
                            characteristic,
                            operation.value,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                        if (result != BluetoothStatusCodes.SUCCESS) {
                            operation.completion.complete(BleResult.Error("Failed to start write: $result"))
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = operation.value
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        @Suppress("DEPRECATION")
                        if (!gatt.writeCharacteristic(characteristic)) {
                            operation.completion.complete(BleResult.Error("Failed to start write"))
                        }
                    }
                } else {
                    operation.completion.complete(BleResult.Error("Characteristic not found"))
                }
            }

            is BleOperation.EnableNotifications -> {
                val characteristic =
                    getCharacteristic(gatt, operation.serviceUuid, operation.charUuid)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)

                    // We also need to write to the descriptor to actually enable it on the device
                    val descriptor =
                        characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val result = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            if (result != BluetoothStatusCodes.SUCCESS) {
                                operation.completion.complete(BleResult.Error("Failed to write descriptor: $result"))
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            if (!gatt.writeDescriptor(descriptor)) {
                                operation.completion.complete(BleResult.Error("Failed to write descriptor"))
                            }
                        }
                    } else {
                        // Some devices don't need descriptor, or it's missing. Signal success or error?
                        // Signal success for local enable
                        operation.completion.complete(BleResult.Success(null))
                    }
                } else {
                    operation.completion.complete(BleResult.Error("Characteristic not found"))
                }
            }
        }
    }

    private fun getCharacteristic(
        gatt: BluetoothGatt,
        serviceUuid: UUID,
        charUuid: UUID
    ): BluetoothGattCharacteristic? {
        return gatt.getService(serviceUuid)?.getCharacteristic(charUuid)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.CONNECTED
                // Auto discover services could be called here, or manually
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (pendingOperation is BleOperation.DiscoverServices) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingOperation?.completion?.complete(BleResult.Success(null))
                } else {
                    pendingOperation?.completion?.complete(BleResult.Error("Service discovery failed: $status"))
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            handleReadResult(characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleReadResult(value, status)
        }

        private fun handleReadResult(value: ByteArray?, status: Int) {
            if (pendingOperation is BleOperation.ReadCharacteristic) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingOperation?.completion?.complete(BleResult.Success(value))
                } else {
                    pendingOperation?.completion?.complete(BleResult.Error("Read failed: $status"))
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (pendingOperation is BleOperation.WriteCharacteristic) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingOperation?.completion?.complete(BleResult.Success(null))
                } else {
                    pendingOperation?.completion?.complete(BleResult.Error("Write failed: $status"))
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Usually for notifications
            if (pendingOperation is BleOperation.EnableNotifications) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingOperation?.completion?.complete(BleResult.Success(null))
                } else {
                    pendingOperation?.completion?.complete(BleResult.Error("Descriptor write failed: $status"))
                }
            }
        }

        @Deprecated(
            "Deprecated in Java",
            ReplaceWith("onCharacteristicChanged(gatt, characteristic, characteristic.value)")
        )
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleCharacteristicChanged(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

        private fun handleCharacteristicChanged(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val notification = BleNotification(
                characteristic.service.uuid,
                characteristic.uuid,
                value
            )
            _notifications.trySend(notification)
        }
    }
}

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

// Sealed classes for operations
sealed class BleOperation {
    val completion = CompletableDeferred<BleResult>()

    object DiscoverServices : BleOperation()
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
}

sealed class BleResult {
    data class Success(val data: ByteArray?) : BleResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data?.contentHashCode() ?: 0
        }
    }

    data class Error(val message: String) : BleResult()
}
