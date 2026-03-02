package com.edts.blesdk.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.edts.blesdk.model.BleDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BleManagerTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var adapter: BluetoothAdapter

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        adapter = mockk(relaxed = true)

        every { context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns adapter
    }

    @Test
    fun isBluetoothEnabled_adapterEnabled_returnsTrue() {
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)
        assertTrue(manager.isBluetoothEnabled())
    }

    @Test
    fun isBluetoothEnabled_adapterDisabled_returnsFalse() {
        every { adapter.isEnabled } returns false
        val manager = BleManager(context)
        assertFalse(manager.isBluetoothEnabled())
    }

    @Test
    fun isBluetoothEnabled_adapterNull_returnsFalse() {
        every { bluetoothManager.adapter } returns null
        val manager = BleManager(context)
        assertFalse(manager.isBluetoothEnabled())
    }

    @Test
    fun connect_bluetoothDisabled_returnsNull() {
        every { adapter.isEnabled } returns false
        val manager = BleManager(context)
        val device = BleDevice("Test", "00:11", -50, mockk())
        val connection = manager.connect(device)
        assertNull(connection)
    }

    @Test
    fun connect_bluetoothEnabledWithDevice_returnsConnection() {
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)
        val bluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
        val device = BleDevice("Test", "00:11", -50, bluetoothDevice)
        val connection = manager.connect(device)
        assertNotNull(connection)
    }

    @Test
    fun connect_bluetoothEnabledWithoutDevice_returnsNull() {
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)
        val device = BleDevice("Test", "00:11", -50, null)
        val connection = manager.connect(device)
        assertNull(connection)
    }
}
