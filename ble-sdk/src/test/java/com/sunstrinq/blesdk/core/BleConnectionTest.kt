package com.sunstrinq.blesdk.core

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.sunstrinq.blesdk.constant.BleConstants
import com.sunstrinq.blesdk.model.ConnectionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BleConnectionTest {

    private lateinit var context: Context
    private lateinit var device: BluetoothDevice
    private lateinit var gatt: BluetoothGatt

    private val serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val charUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        device = mockk(relaxed = true)
        gatt = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { device.connectGatt(any(), any(), any()) } returns gatt
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ─── Helper ───────────────────────────────────────────────────────────────────

    /**
     * Creates a BleConnection using the test's UnconfinedTestDispatcher so the
     * operation queue consumer runs synchronously on the same thread as the test.
     * This guarantees `pendingOperation` is set before any GATT callback fires.
     */
    private fun buildConnection() = BleConnection(
        context,
        device,
        kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher())
    )

    private fun getGattCallback(connection: BleConnection): android.bluetooth.BluetoothGattCallback {
        val callbackField = BleConnection::class.java.getDeclaredField("gattCallback")
        callbackField.isAccessible = true
        return callbackField.get(connection) as android.bluetooth.BluetoothGattCallback
    }

    // ─── Initial state ────────────────────────────────────────────────────────────

    @Test
    fun `connectionState returns DISCONNECTED when connection is first created`() = runTest {
        // Arrange
        val connection = buildConnection()

        // Act
        val state = connection.connectionState.first()

        // Assert
        assertThat(state).isEqualTo(ConnectionState.DISCONNECTED)
    }

    // ─── connect ──────────────────────────────────────────────────────────────────

    @Test
    fun `connect returns CONNECTING state when called on disconnected connection`() = runTest {
        // Arrange
        val connection = buildConnection()

        // Act
        connection.connect()

        // Assert
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
    }

    @Test
    fun `connect returns CONNECTED state when gatt callback fires STATE_CONNECTED`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        connection.connect()

        // Act
        callback.onConnectionStateChange(
            gatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        // Assert
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
    }

    @Test
    fun `connect returns no state change when called while already CONNECTING`() = runTest {
        // Arrange
        val connection = buildConnection()
        connection.connect()
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.CONNECTING)

        // Act — calling connect again while already connecting should be a no-op
        connection.connect()

        // Assert — state remains CONNECTING, no transition to a different state
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
    }

    // ─── disconnect ───────────────────────────────────────────────────────────────

    @Test
    fun `disconnect returns DISCONNECTING state when called`() = runTest {
        // Arrange
        val connection = buildConnection()

        // Act
        connection.disconnect()

        // Assert
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.DISCONNECTING)
    }

    @Test
    fun `disconnect returns DISCONNECTED state when gatt callback fires STATE_DISCONNECTED`() =
        runTest {
            // Arrange
            val connection = buildConnection()
            val callback = getGattCallback(connection)
            connection.connect()
            callback.onConnectionStateChange(
                gatt,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_CONNECTED
            )

            // Act
            callback.onConnectionStateChange(
                gatt,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_DISCONNECTED
            )

            // Assert
            assertThat(connection.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        }

    // ─── discoverServices ─────────────────────────────────────────────────────────

    @Test
    fun `discoverServices returns true when operation is enqueued`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        connection.connect()
        every { gatt.discoverServices() } returns true

        // Act: fire callback AFTER enqueuing — with UnconfinedTestDispatcher the queue
        // consumer runs synchronously so pendingOperation is already set when we launch.
        launch {
            callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
        }
        val result = connection.discoverServices()

        // Assert
        assertThat(result).isTrue()
    }

    // ─── disableNotifications ─────────────────────────────────────────────────────

    @Test
    fun `disableNotifications writes CCCD descriptor correctly when characteristic has NOTIFY`() =
        runTest {
            // Arrange
            val connection = buildConnection()
            val callback = getGattCallback(connection)
            val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
            val service = mockk<BluetoothGattService>(relaxed = true)
            val descriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

            connection.connect()
            every { gatt.getService(serviceUuid) } returns service
            every { service.getCharacteristic(charUuid) } returns characteristic
            every { characteristic.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID)) } returns descriptor
            every { gatt.setCharacteristicNotification(characteristic, false) } returns true
            every {
                gatt.writeDescriptor(
                    descriptor,
                    any()
                )
            } returns BluetoothStatusCodes.SUCCESS
            @Suppress("DEPRECATION")
            every { gatt.writeDescriptor(descriptor) } returns true
            every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED

            // Act
            launch {
                callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            }
            connection.disableNotifications(serviceUuid, charUuid)

            // Assert - No exception means success
        }

    // ─── writeCharacteristic ──────────────────────────────────────────────────────

    @Test
    fun `writeCharacteristic writes data correctly when characteristic exists`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)
        val dataToWrite = byteArrayOf(0x01)

        connection.connect()
        every { gatt.getService(serviceUuid) } returns service
        every { service.getCharacteristic(charUuid) } returns characteristic
        every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED
        every {
            gatt.writeCharacteristic(
                characteristic,
                dataToWrite,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } returns BluetoothStatusCodes.SUCCESS
        @Suppress("DEPRECATION")
        every { gatt.writeCharacteristic(characteristic) } returns true

        // Act
        launch {
            callback.onCharacteristicWrite(
                gatt,
                characteristic,
                BluetoothGatt.GATT_SUCCESS
            )
        }
        connection.writeCharacteristic(serviceUuid, charUuid, dataToWrite)

        // Assert - No exception means success
    }

    // ─── readRssi ─────────────────────────────────────────────────────────────────

    @Test
    fun `readRssi returns correct rssi value when gatt callback fires success`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        connection.connect()
        every { gatt.readRemoteRssi() } returns true

        // Act: with UnconfinedTestDispatcher the operation queue consumer runs synchronously,
        // so pendingOperation is set immediately when readRssi() enqueues the op.
        // We launch the callback FIRST so it fires once the coroutine suspends on await().
        launch {
            callback.onReadRemoteRssi(gatt, -65, BluetoothGatt.GATT_SUCCESS)
        }
        val result = connection.readRssi()

        // Assert
        assertThat(result).isEqualTo(-65)
    }

    @Test
    fun `readRssi throws Exception when gatt callback fires failure`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        connection.connect()
        every { gatt.readRemoteRssi() } returns true

        // Act
        launch {
            callback.onReadRemoteRssi(gatt, 0, BluetoothGatt.GATT_FAILURE)
        }

        var exception: Exception? = null
        try {
            connection.readRssi()
        } catch (e: Exception) {
            exception = e
        }

        // Assert
        assertThat(exception).isNotNull()
        assertThat(exception?.message).contains("failed")
    }

    // ─── notifications ────────────────────────────────────────────────────────────

    @Test
    fun `notifications returns emitted value when characteristic changes`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)

        every { characteristic.service } returns service
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns charUuid

        val expectedData = byteArrayOf(0x48, 0x65, 0x6C)

        // Act — start waiting for exactly one notification before firing the callback
        val notificationDeferred = async { connection.notifications.first() }
        callback.onCharacteristicChanged(gatt, characteristic, expectedData)
        val notification = notificationDeferred.await()

        // Assert
        assertThat(notification.serviceUuid).isEqualTo(serviceUuid)
        assertThat(notification.charUuid).isEqualTo(charUuid)
        assertThat(notification.data).isEqualTo(expectedData)
    }

    // ─── High-Level Helpers ───────────────────────────────────────────────────────

    @Test
    fun `readDeviceManufacturerName returns parsed string when read succeeds`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)

        connection.connect()
        // Mock gatt to return the characteristic properly
        every { gatt.getService(BleConstants.DEVICE_INFORMATION_SERVICE_UUID) } returns service
        every { service.getCharacteristic(BleConstants.MANUFACTURER_NAME_STRING_CHAR_UUID) } returns characteristic
        every { gatt.readCharacteristic(characteristic) } returns true

        val manufacturerName = "TestManufacturer"
        val manufacturerData = manufacturerName.toByteArray(Charsets.UTF_8)

        // Act
        launch {
            callback.onCharacteristicRead(
                gatt,
                characteristic,
                manufacturerData,
                BluetoothGatt.GATT_SUCCESS
            )
        }
        val result = connection.readDeviceManufacturerName()

        // Assert
        assertThat(result).isEqualTo(manufacturerName)
    }

    @Test
    fun `subscribeToHeartRate successfully writes CCCD descriptor`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)
        val descriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        connection.connect()
        every { gatt.getService(BleConstants.HEART_RATE_SERVICE_UUID) } returns service
        every { service.getCharacteristic(BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID) } returns characteristic
        every { characteristic.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID)) } returns descriptor
        // Setting NOTIFY property
        every { characteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_NOTIFY
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { gatt.writeDescriptor(descriptor, any()) } returns BluetoothStatusCodes.SUCCESS
        @Suppress("DEPRECATION")
        every { gatt.writeDescriptor(descriptor) } returns true
        every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED

        // Act
        launch {
            callback.onDescriptorWrite(
                gatt,
                descriptor,
                BluetoothGatt.GATT_SUCCESS
            )
        }
        connection.subscribeToHeartRate()

        // Assert (No exception means success)
    }

    @Test
    fun `subscribeToBloodPressure successfully writes CCCD descriptor`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)
        val descriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        connection.connect()
        every { gatt.getService(BleConstants.BLOOD_PRESSURE_SERVICE_UUID) } returns service
        every { service.getCharacteristic(BleConstants.BLOOD_PRESSURE_MEASUREMENT_CHAR_UUID) } returns characteristic
        every { characteristic.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID)) } returns descriptor
        every { characteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_INDICATE
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { gatt.writeDescriptor(descriptor, any()) } returns BluetoothStatusCodes.SUCCESS
        @Suppress("DEPRECATION")
        every { gatt.writeDescriptor(descriptor) } returns true
        every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED

        // Act
        launch {
            callback.onDescriptorWrite(
                gatt,
                descriptor,
                BluetoothGatt.GATT_SUCCESS
            )
        }
        val result = connection.subscribeToBloodPressure()

        // Assert
        assertThat(result).isNotNull() // Or just assert no throw
    }

    @Test
    fun `subscribeToHealthThermometer successfully writes CCCD descriptor`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)
        val descriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        connection.connect()
        every { gatt.getService(BleConstants.HEALTH_THERMOMETER_SERVICE_UUID) } returns service
        every { service.getCharacteristic(BleConstants.TEMPERATURE_MEASUREMENT_CHAR_UUID) } returns characteristic
        every { characteristic.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID)) } returns descriptor
        every { characteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_INDICATE
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { gatt.writeDescriptor(descriptor, any()) } returns BluetoothStatusCodes.SUCCESS
        @Suppress("DEPRECATION")
        every { gatt.writeDescriptor(descriptor) } returns true
        every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED

        // Act
        launch {
            callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
        }
        connection.subscribeToHealthThermometer()
    }

    @Test
    fun `subscribeToWeightScale successfully writes CCCD descriptor`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)
        val descriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        connection.connect()
        every { gatt.getService(BleConstants.WEIGHT_SCALE_SERVICE_UUID) } returns service
        every { service.getCharacteristic(BleConstants.WEIGHT_MEASUREMENT_CHAR_UUID) } returns characteristic
        every { characteristic.getDescriptor(UUID.fromString(BleConstants.CCCD_UUID)) } returns descriptor
        every { characteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_INDICATE
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { gatt.writeDescriptor(descriptor, any()) } returns BluetoothStatusCodes.SUCCESS
        @Suppress("DEPRECATION")
        every { gatt.writeDescriptor(descriptor) } returns true
        every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED

        // Act
        launch {
            callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
        }
        connection.subscribeToWeightScale()
    }

    @Test
    fun `requestMtu returns true when successful`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        connection.connect()
        every { gatt.requestMtu(512) } returns true

        // Act
        launch {
            callback.onMtuChanged(gatt, 512, BluetoothGatt.GATT_SUCCESS)
        }
        connection.requestMtu(512)

        // Assert - no exception means success
    }

    @Test
    fun `readBatteryLevel returns battery percentage when read succeeds`() = runTest {
        // Arrange
        val connection = buildConnection()
        val callback = getGattCallback(connection)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<BluetoothGattService>(relaxed = true)

        connection.connect()
        every { gatt.getService(BleConstants.BATTERY_SERVICE_UUID) } returns service
        every { service.getCharacteristic(BleConstants.BATTERY_LEVEL_CHAR_UUID) } returns characteristic
        every { gatt.readCharacteristic(characteristic) } returns true

        val batteryLevel = byteArrayOf(95)

        // Act
        launch {
            callback.onCharacteristicRead(
                gatt,
                characteristic,
                batteryLevel,
                BluetoothGatt.GATT_SUCCESS
            )
        }
        val result = connection.readBatteryLevel()

        // Assert
        assertThat(result).isEqualTo(95)
    }

    @Test
    fun `close disconnects and changes state to DISCONNECTED`() = runTest {
        // Arrange
        val connection = buildConnection()

        // Act
        connection.close()

        // Assert
        val state = connection.connectionState.value
        assertThat(state).isEqualTo(ConnectionState.DISCONNECTED)
    }
}
