package com.sunstrinq.blesdk.core

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
import com.sunstrinq.blesdk.model.BleOperation
import com.sunstrinq.blesdk.model.BleResult
import com.sunstrinq.blesdk.model.BleNotification
import com.sunstrinq.blesdk.model.ConnectionState
import com.sunstrinq.blesdk.constant.BleConstants
@SuppressLint("MissingPermission")
class BleConnection(
    context: Context,
    private val bluetoothDevice: BluetoothDevice,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val appContext = context.applicationContext
    private var bluetoothGatt: BluetoothGatt? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Operation Queue to serialize GATT operations
    private val operationQueue = Channel<BleOperation>(Channel.UNLIMITED)
    private val scope = coroutineScope
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
                    val result = withTimeoutOrNull(30000) {
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

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING || _connectionState.value == ConnectionState.CONNECTED) return
        _connectionState.value = ConnectionState.CONNECTING
        bluetoothGatt = bluetoothDevice.connectGatt(appContext, false, gattCallback)
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTING
        bluetoothGatt?.disconnect()
    }

    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        _notifications.close()
    }

    suspend fun discoverServices(): Boolean {
        enqueueOperation(BleOperation.DiscoverServices())
        return true
    }

    suspend fun requestMtu(mtu: Int) {
        val op = BleOperation.RequestMtu(mtu)
        enqueueOperation(op)
        val result = op.completion.await()
        if (result is BleResult.Error) throw Exception(result.message)
    }

    suspend fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): ByteArray? {
        val op = BleOperation.ReadCharacteristic(serviceUuid, charUuid)
        enqueueOperation(op)
        val result = op.completion.await()
        if (result is BleResult.Error) throw Exception(result.message)
        return (result as? BleResult.Success)?.data
    }

    suspend fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, value: ByteArray) {
        val op = BleOperation.WriteCharacteristic(serviceUuid, charUuid, value)
        enqueueOperation(op)
        val result = op.completion.await()
        if (result is BleResult.Error) throw Exception(result.message)
    }

    suspend fun enableNotifications(serviceUuid: UUID, charUuid: UUID) {
        val op = BleOperation.EnableNotifications(serviceUuid, charUuid)
        enqueueOperation(op)
        val result = op.completion.await()
        if (result is BleResult.Error) throw Exception(result.message)
    }

    suspend fun disableNotifications(serviceUuid: UUID, charUuid: UUID) {
        val op = BleOperation.DisableNotifications(serviceUuid, charUuid)
        enqueueOperation(op)
        val result = op.completion.await()
        if (result is BleResult.Error) throw Exception(result.message)
    }

    suspend fun readRssi(): Int? {
        val op = BleOperation.ReadRssi()
        enqueueOperation(op)
        val result = op.completion.await()
        if (result is BleResult.Error) throw Exception(result.message)
        return if (result is BleResult.SuccessRssi) result.rssi else null
    }

    // --- Standard SIG High-Level Helpers ---

    suspend fun readBatteryLevel(): Int? {
        val data = readCharacteristic(
            BleConstants.BATTERY_SERVICE_UUID,
            BleConstants.BATTERY_LEVEL_CHAR_UUID
        ) ?: return null
        return com.sunstrinq.blesdk.util.BleExtensions.parseBatteryLevel(data)
    }

    suspend fun readDeviceManufacturerName(): String? {
        val data = readCharacteristic(
            BleConstants.DEVICE_INFORMATION_SERVICE_UUID,
            BleConstants.MANUFACTURER_NAME_STRING_CHAR_UUID
        ) ?: return null
        return com.sunstrinq.blesdk.util.BleExtensions.parseString(data)
    }

    suspend fun subscribeToHeartRate() {
        enableNotifications(
            BleConstants.HEART_RATE_SERVICE_UUID,
            BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID
        )
    }

    suspend fun subscribeToBloodPressure() {
        enableNotifications(
            BleConstants.BLOOD_PRESSURE_SERVICE_UUID,
            BleConstants.BLOOD_PRESSURE_MEASUREMENT_CHAR_UUID
        )
    }

    suspend fun subscribeToHealthThermometer() {
        enableNotifications(
            BleConstants.HEALTH_THERMOMETER_SERVICE_UUID,
            BleConstants.TEMPERATURE_MEASUREMENT_CHAR_UUID
        )
    }

    suspend fun subscribeToWeightScale() {
        enableNotifications(
            BleConstants.WEIGHT_SCALE_SERVICE_UUID,
            BleConstants.WEIGHT_MEASUREMENT_CHAR_UUID
        )
    }

    private suspend fun enqueueOperation(operation: BleOperation) {
        operationQueue.send(operation)
    }

    private fun executeOperation(operation: BleOperation) {
        val gatt = bluetoothGatt ?: run {
            operation.completion.complete(BleResult.Error("Not connected"))
            return
        }

        try {
            when (operation) {
                is BleOperation.DiscoverServices -> {
                    if (!gatt.discoverServices()) {
                        operation.completion.complete(BleResult.Error("Failed to start service discovery"))
                    }
                }

                is BleOperation.RequestMtu -> {
                    if (!gatt.requestMtu(operation.mtu)) {
                        operation.completion.complete(BleResult.Error("Failed to request MTU"))
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
                    // Wait if the device is currently bonding
                    while (gatt.device.bondState == BluetoothDevice.BOND_BONDING) {
                        Thread.sleep(100)
                    }

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
                            characteristic.writeType =
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
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
                    // Wait if the device is currently bonding
                    while (gatt.device.bondState == BluetoothDevice.BOND_BONDING) {
                        Thread.sleep(100)
                    }

                    val characteristic =
                        getCharacteristic(gatt, operation.serviceUuid, operation.charUuid)
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor =
                            characteristic.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID))
                        if (descriptor != null) {
                            val descriptorValue =
                                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                } else {
                                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val result = gatt.writeDescriptor(descriptor, descriptorValue)
                                if (result != BluetoothStatusCodes.SUCCESS) {
                                    operation.completion.complete(BleResult.Error("Failed to write descriptor: $result"))
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = descriptorValue
                                @Suppress("DEPRECATION")
                                if (!gatt.writeDescriptor(descriptor)) {
                                    operation.completion.complete(BleResult.Error("Failed to write descriptor"))
                                }
                            }
                        } else {
                            operation.completion.complete(BleResult.Success(null))
                        }
                    } else {
                        operation.completion.complete(BleResult.Error("Characteristic not found"))
                    }
                }

                is BleOperation.DisableNotifications -> {
                    val characteristic =
                        getCharacteristic(gatt, operation.serviceUuid, operation.charUuid)
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, false)

                        val descriptor =
                            characteristic.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID))
                        if (descriptor != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val result = gatt.writeDescriptor(
                                    descriptor,
                                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                )
                                if (result != BluetoothStatusCodes.SUCCESS) {
                                    operation.completion.complete(BleResult.Error("Failed to write descriptor: $result"))
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value =
                                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                if (!gatt.writeDescriptor(descriptor)) {
                                    operation.completion.complete(BleResult.Error("Failed to write descriptor"))
                                }
                            }
                        } else {
                            operation.completion.complete(BleResult.Success(null))
                        }
                    } else {
                        operation.completion.complete(BleResult.Error("Characteristic not found"))
                    }
                }

                is BleOperation.ReadRssi -> {
                    if (!gatt.readRemoteRssi()) {
                        operation.completion.complete(BleResult.Error("Failed to start RSSI read"))
                    }
                }
            }
        } catch (e: SecurityException) {
            operation.completion.complete(BleResult.Error("SecurityException: Missing BLE permissions"))
            operation.completion.complete(BleResult.Error("Error: ${e.message}"))
        } catch (e: Exception) {
            operation.completion.complete(BleResult.Error("Exception during GATT operation: ${e.message}"))
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

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (pendingOperation is BleOperation.ReadRssi) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingOperation?.completion?.complete(BleResult.SuccessRssi(rssi))
                } else {
                    pendingOperation?.completion?.complete(BleResult.Error("RSSI read failed: $status"))
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (pendingOperation is BleOperation.RequestMtu) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingOperation?.completion?.complete(BleResult.Success(null))
                } else {
                    pendingOperation?.completion?.complete(
                        BleResult.Error(
                            getGattErrorMessage(
                                "MTU request failed",
                                status
                            )
                        )
                    )
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
                    pendingOperation?.completion?.complete(
                        BleResult.Error(
                            getGattErrorMessage(
                                "Read failed",
                                status
                            )
                        )
                    )
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
                    pendingOperation?.completion?.complete(
                        BleResult.Error(
                            getGattErrorMessage(
                                "Write failed",
                                status
                            )
                        )
                    )
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (pendingOperation is BleOperation.EnableNotifications || pendingOperation is BleOperation.DisableNotifications) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingOperation?.completion?.complete(BleResult.Success(null))
                } else {
                    pendingOperation?.completion?.complete(
                        BleResult.Error(
                            getGattErrorMessage(
                                "Descriptor write failed",
                                status
                            )
                        )
                    )
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

        private fun getGattErrorMessage(baseMessage: String, status: Int): String {
            return when (status) {
                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
                BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION,
                BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "$baseMessage: Secure Bonding Required (Status $status)"

                133 -> "$baseMessage: Connection/GATT Error 133 (Device may require pairing or restarted)"
                BluetoothGatt.GATT_CONNECTION_CONGESTED -> "$baseMessage: Connection Congested"
                else -> "$baseMessage: Status $status"
            }
        }
    }
}
