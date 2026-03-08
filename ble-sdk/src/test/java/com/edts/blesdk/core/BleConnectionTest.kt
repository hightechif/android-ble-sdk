package com.edts.blesdk.core

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.edts.blesdk.model.ConnectionState
import com.google.common.truth.Truth.assertThat
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
    private lateinit var gatt: android.bluetooth.BluetoothGatt

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
            android.bluetooth.BluetoothGatt.GATT_SUCCESS,
            android.bluetooth.BluetoothProfile.STATE_CONNECTED
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
                android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                android.bluetooth.BluetoothProfile.STATE_CONNECTED
            )

            // Act
            callback.onConnectionStateChange(
                gatt,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
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
            callback.onServicesDiscovered(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS)
        }
        val result = connection.discoverServices()

        // Assert
        assertThat(result).isTrue()
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
            callback.onReadRemoteRssi(gatt, -65, android.bluetooth.BluetoothGatt.GATT_SUCCESS)
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
            callback.onReadRemoteRssi(gatt, 0, android.bluetooth.BluetoothGatt.GATT_FAILURE)
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
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)

        every { characteristic.service } returns service
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns charUuid

        val expectedData = byteArrayOf(0x48, 0x65, 0x6C)
        @Suppress("DEPRECATION")
        every { characteristic.value } returns expectedData

        // Act — start waiting for exactly one notification before firing the callback
        val notificationDeferred = async { connection.notifications.first() }
        callback.onCharacteristicChanged(gatt, characteristic)
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
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)

        connection.connect()
        // Mock gatt to return the characteristic properly
        every { gatt.getService(com.edts.blesdk.constant.BleConstants.DEVICE_INFORMATION_SERVICE_UUID) } returns service
        every { service.getCharacteristic(com.edts.blesdk.constant.BleConstants.MANUFACTURER_NAME_STRING_CHAR_UUID) } returns characteristic
        every { gatt.readCharacteristic(characteristic) } returns true

        val manufacturerName = "TestManufacturer"
        val manufacturerData = manufacturerName.toByteArray(Charsets.UTF_8)

        // Act
        launch {
            callback.onCharacteristicRead(
                gatt,
                characteristic,
                manufacturerData,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS
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
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)
        val descriptor = mockk<android.bluetooth.BluetoothGattDescriptor>(relaxed = true)

        connection.connect()
        every { gatt.getService(com.edts.blesdk.constant.BleConstants.HEART_RATE_SERVICE_UUID) } returns service
        every { service.getCharacteristic(com.edts.blesdk.constant.BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID) } returns characteristic
        every { characteristic.getDescriptor(UUID.fromString(com.edts.blesdk.constant.BleConstants.CCCD_UUID)) } returns descriptor
        // Setting NOTIFY property
        every { characteristic.properties } returns android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { gatt.writeDescriptor(descriptor, any()) } returns android.bluetooth.BluetoothStatusCodes.SUCCESS
        @Suppress("DEPRECATION")
        every { gatt.writeDescriptor(descriptor) } returns true
        every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED

        // Act
        launch {
            callback.onDescriptorWrite(
                gatt,
                descriptor,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS
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
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)
        val descriptor = mockk<android.bluetooth.BluetoothGattDescriptor>(relaxed = true)

        connection.connect()
        every { gatt.getService(com.edts.blesdk.constant.BleConstants.BLOOD_PRESSURE_SERVICE_UUID) } returns service
        every { service.getCharacteristic(com.edts.blesdk.constant.BleConstants.BLOOD_PRESSURE_MEASUREMENT_CHAR_UUID) } returns characteristic
        every { characteristic.getDescriptor(UUID.fromString(com.edts.blesdk.constant.BleConstants.CCCD_UUID)) } returns descriptor
        every { characteristic.properties } returns android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { gatt.writeDescriptor(descriptor, any()) } returns android.bluetooth.BluetoothStatusCodes.SUCCESS
        @Suppress("DEPRECATION")
        every { gatt.writeDescriptor(descriptor) } returns true
        every { gatt.device.bondState } returns BluetoothDevice.BOND_BONDED

        // Act
        launch {
            callback.onDescriptorWrite(
                gatt,
                descriptor,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS
            )
        }
        connection.subscribeToBloodPressure()
    }
}
