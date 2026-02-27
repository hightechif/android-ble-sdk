package com.edts.blesdk.core

import android.bluetooth.BluetoothDevice
import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleConnectionTest {

    @Test
    fun initialConnectionState_isDisconnected() = runTest {
        val context = mockk<Context>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        
        val connection = BleConnection(context, device)

        val state = connection.connectionState.first()
        assertEquals(ConnectionState.DISCONNECTED, state)
    }

    @Test
    fun connect_changesStateToConnecting() = runTest {
        val context = mockk<Context>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        
        val connection = BleConnection(context, device)
        connection.connect()

        val state = connection.connectionState.first()
        assertEquals(ConnectionState.CONNECTING, state)
    }

    @Test
    fun disconnect_changesStateToDisconnecting() = runTest {
        val context = mockk<Context>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        
        val connection = BleConnection(context, device)
        connection.disconnect()

        val state = connection.connectionState.first()
        assertEquals(ConnectionState.DISCONNECTING, state)
    }

    @Test
    fun discoverServices_enqueuesOperationAndReturnsTrue() = runTest {
        val context = mockk<Context>(relaxed = true)
        val device = mockk<BluetoothDevice>(relaxed = true)
        
        val connection = BleConnection(context, device)
        // Set up GATT so executeOperation doesn't fail early
        val gatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        io.mockk.every { device.connectGatt(any(), any(), any()) } returns gatt
        connection.connect()

        // Wait a bit to ensure the queue processes it
        kotlinx.coroutines.delay(10)
        
        // This will block until the operation queue completes it.
        // We simulate the GATT callback firing to complete the pending operation.
        launch {
            kotlinx.coroutines.delay(50)
            // We need to access the private gattCallback to simulate onServicesDiscovered
            val callbackField = BleConnection::class.java.getDeclaredField("gattCallback")
            callbackField.isAccessible = true
            val callback = callbackField.get(connection) as android.bluetooth.BluetoothGattCallback
            callback.onServicesDiscovered(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS)
        }
        
        val result = connection.discoverServices()
        assertEquals(true, result)
    }
}
