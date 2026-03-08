package com.sunstrinq.blesdk.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.sunstrinq.blesdk.model.BleDevice
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test

class BleManagerTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var adapter: BluetoothAdapter

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        adapter = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns adapter
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ─── isBluetoothEnabled ───────────────────────────────────────────────────────

    @Test
    fun `isBluetoothEnabled returns true when adapter is enabled`() {
        // Arrange
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)

        // Act
        val result = manager.isBluetoothEnabled()

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `isBluetoothEnabled returns false when adapter is disabled`() {
        // Arrange
        every { adapter.isEnabled } returns false
        val manager = BleManager(context)

        // Act
        val result = manager.isBluetoothEnabled()

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `isBluetoothEnabled returns false when adapter is null`() {
        // Arrange
        every { bluetoothManager.adapter } returns null
        val manager = BleManager(context)

        // Act
        val result = manager.isBluetoothEnabled()

        // Assert
        assertThat(result).isFalse()
    }

    // ─── connect ──────────────────────────────────────────────────────────────────

    @Test
    fun `connect returns null when bluetooth is disabled`() {
        // Arrange
        every { adapter.isEnabled } returns false
        val manager = BleManager(context)
        val device = BleDevice("Test", "00:11:22:33:44:55", -50, mockk())

        // Act
        val result = manager.connect(device)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `connect returns BleConnection when bluetooth is enabled and device has BluetoothDevice`() {
        // Arrange
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)
        val bluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
        val device = BleDevice("Test", "00:11:22:33:44:55", -50, bluetoothDevice)

        // Act
        val result = manager.connect(device)

        // Assert
        assertThat(result).isNotNull()
    }

    @Test
    fun `connect returns null when bluetooth is enabled but device has no BluetoothDevice`() {
        // Arrange
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)
        val device = BleDevice("Test", "00:11:22:33:44:55", -50, null)

        // Act
        val result = manager.connect(device)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `connect returns BleConnection instance when device is valid and bluetooth enabled`() {
        // Arrange
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)
        val bluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
        val device = BleDevice("MyDevice", "AA:BB:CC:DD:EE:FF", -60, bluetoothDevice)

        // Act
        val result = manager.connect(device)

        // Assert
        assertThat(result).isInstanceOf(BleConnection::class.java)
    }

    // ─── scanner ──────────────────────────────────────────────────────────────────

    @Test
    fun `scanner returns BleScanner instance when adapter is available`() {
        // Arrange
        every { adapter.isEnabled } returns true
        val manager = BleManager(context)

        // Act
        val scanner = manager.scanner

        // Assert
        assertThat(scanner).isInstanceOf(BleScanner::class.java)
    }

    @Test
    fun `scanner throws IllegalStateException when bluetooth adapter is null`() {
        // Arrange
        every { bluetoothManager.adapter } returns null
        val manager = BleManager(context)

        // Act
        var thrownException: Exception? = null
        try {
            @Suppress("UNUSED_EXPRESSION")
            manager.scanner
        } catch (e: IllegalStateException) {
            thrownException = e
        }

        // Assert
        assertThat(thrownException).isNotNull()
        assertThat(thrownException).isInstanceOf(IllegalStateException::class.java)
        assertThat(thrownException?.message).contains("Bluetooth is not supported")
    }
}
