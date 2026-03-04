package com.edts.blesdk.core

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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

    private fun getGattCallback(connection: BleConnection): android.bluetooth.BluetoothGattCallback {
        val callbackField = BleConnection::class.java.getDeclaredField("gattCallback")
        callbackField.isAccessible = true
        return callbackField.get(connection) as android.bluetooth.BluetoothGattCallback
    }

    // ─── Initial state ────────────────────────────────────────────────────────────

    @Test
    fun `connectionState returns DISCONNECTED when connection is first created`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)

        // Act
        val state = connection.connectionState.first()

        // Assert
        assertThat(state).isEqualTo(ConnectionState.DISCONNECTED)
    }

    // ─── connect ──────────────────────────────────────────────────────────────────

    @Test
    fun `connect returns CONNECTING state when called on disconnected connection`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)

        // Act
        connection.connect()

        // Assert
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
    }

    @Test
    fun `connect returns CONNECTED state when gatt callback fires STATE_CONNECTED`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)
        val callback = getGattCallback(connection)
        connection.connect()

        // Act
        callback.onConnectionStateChange(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS, android.bluetooth.BluetoothProfile.STATE_CONNECTED)

        // Assert
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
    }

    @Test
    fun `connect returns no state change when called while already CONNECTING`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)
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
        val connection = BleConnection(context, device)

        // Act
        connection.disconnect()

        // Assert
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.DISCONNECTING)
    }

    @Test
    fun `disconnect returns DISCONNECTED state when gatt callback fires STATE_DISCONNECTED`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)
        val callback = getGattCallback(connection)
        connection.connect()
        callback.onConnectionStateChange(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS, android.bluetooth.BluetoothProfile.STATE_CONNECTED)

        // Act
        callback.onConnectionStateChange(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS, android.bluetooth.BluetoothProfile.STATE_DISCONNECTED)

        // Assert
        assertThat(connection.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
    }

    // ─── discoverServices ─────────────────────────────────────────────────────────

    @Test
    fun `discoverServices returns true when operation is enqueued`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)
        val callback = getGattCallback(connection)
        connection.connect()
        every { gatt.discoverServices() } returns true

        // Act: simulate the GATT callback completing the operation in the background
        launch {
            kotlinx.coroutines.delay(50)
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
        val connection = BleConnection(context, device)
        val callback = getGattCallback(connection)
        connection.connect()
        every { gatt.readRemoteRssi() } returns true

        // Act
        launch {
            kotlinx.coroutines.delay(50)
            callback.onReadRemoteRssi(gatt, -65, android.bluetooth.BluetoothGatt.GATT_SUCCESS)
        }
        val result = connection.readRssi()

        // Assert
        assertThat(result).isEqualTo(-65)
    }

    @Test
    fun `readRssi returns null when gatt callback fires failure`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)
        val callback = getGattCallback(connection)
        connection.connect()
        every { gatt.readRemoteRssi() } returns true

        // Act
        launch {
            kotlinx.coroutines.delay(50)
            callback.onReadRemoteRssi(gatt, 0, android.bluetooth.BluetoothGatt.GATT_FAILURE)
        }
        val result = connection.readRssi()

        // Assert
        assertThat(result).isNull()
    }

    // ─── notifications ────────────────────────────────────────────────────────────

    @Test
    fun `notifications returns emitted value when characteristic changes`() = runTest {
        // Arrange
        val connection = BleConnection(context, device)
        val callback = getGattCallback(connection)
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)

        every { characteristic.service } returns service
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns charUuid

        val received = mutableListOf<BleNotification>()

        // Act
        val collectJob = launch {
            connection.notifications.toList(received)
        }

        val expectedData = byteArrayOf(0x48, 0x65, 0x6C)
        @Suppress("DEPRECATION")
        callback.onCharacteristicChanged(gatt, characteristic.also {
            every { it.value } returns expectedData
        })

        // Close the notifications channel to terminate collection
        connection.close()
        collectJob.join()

        // Assert
        assertThat(received).hasSize(1)
        assertThat(received[0].serviceUuid).isEqualTo(serviceUuid)
        assertThat(received[0].charUuid).isEqualTo(charUuid)
        assertThat(received[0].data).isEqualTo(expectedData)
    }

    // ─── BleResult sealed class ───────────────────────────────────────────────────

    @Test
    fun `BleResult Success returns equal when data arrays are equal`() {
        // Arrange
        val data = byteArrayOf(0x01, 0x02)
        val result1 = BleResult.Success(data)
        val result2 = BleResult.Success(byteArrayOf(0x01, 0x02))

        // Act & Assert
        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun `BleResult SuccessRssi returns correct rssi when instantiated`() {
        // Arrange
        val rssi = -72

        // Act
        val result = BleResult.SuccessRssi(rssi)

        // Assert
        assertThat(result.rssi).isEqualTo(-72)
    }

    @Test
    fun `BleResult Error returns correct message when instantiated`() {
        // Arrange
        val message = "Operation timed out"

        // Act
        val result = BleResult.Error(message)

        // Assert
        assertThat(result.message).isEqualTo(message)
    }

    // ─── BleNotification data class ───────────────────────────────────────────────

    @Test
    fun `BleNotification returns equal when all fields match`() {
        // Arrange
        val data = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val notification1 = BleNotification(serviceUuid, charUuid, data)
        val notification2 = BleNotification(serviceUuid, charUuid, byteArrayOf(0xAB.toByte(), 0xCD.toByte()))

        // Act & Assert
        assertThat(notification1).isEqualTo(notification2)
    }

    @Test
    fun `BleNotification returns not equal when service UUID differs`() {
        // Arrange
        val data = byteArrayOf(0x01)
        val otherUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val notification1 = BleNotification(serviceUuid, charUuid, data)
        val notification2 = BleNotification(otherUuid, charUuid, data)

        // Act & Assert
        assertThat(notification1).isNotEqualTo(notification2)
    }
}
