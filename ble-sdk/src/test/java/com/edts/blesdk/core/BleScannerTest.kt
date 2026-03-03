package com.edts.blesdk.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BleScannerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun buildMockAdapter(leScanner: BluetoothLeScanner? = null): BluetoothAdapter {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.bluetoothLeScanner } returns leScanner
        return adapter
    }

    private fun buildMockLeScanner(): Pair<BluetoothLeScanner, CapturingSlot<ScanCallback>> {
        val leScanner = mockk<BluetoothLeScanner>(relaxed = true)
        val callbackSlot = slot<ScanCallback>()
        every {
            leScanner.startScan(null, any(), capture(callbackSlot))
        } returns Unit
        return leScanner to callbackSlot
    }

    private fun mockDevice(address: String, name: String?, rssi: Int): ScanResult {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns address
        every { device.name } returns name

        val scanResult = mockk<ScanResult>()
        every { scanResult.device } returns device
        every { scanResult.rssi } returns rssi
        return scanResult
    }

    @Test
    fun `startScan emits unique devices into scannedDevices`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)

        callbackSlot.captured.onScanResult(1, mockDevice("AA:BB:CC:DD:EE:FF", "Watch", -50))
        callbackSlot.captured.onScanResult(1, mockDevice("AA:BB:CC:DD:EE:FF", "Watch", -50))
        callbackSlot.captured.onScanResult(1, mockDevice("11:22:33:44:55:66", "Tracker", -65))

        assertEquals(2, scanner.scannedDevices.value.size)
        assertEquals("Watch", scanner.scannedDevices.value[0].name)
        assertEquals("Tracker", scanner.scannedDevices.value[1].name)
    }

    @Test
    fun `startScan with unavailable leScanner sets isScanning to false`() = runTest(testDispatcher) {
        val scanner = BleScanner(buildMockAdapter(leScanner = null))

        scanner.startScan(backgroundScope)
        advanceUntilIdle()

        assertFalse(scanner.isScanning.value)
    }

    @Test
    fun `stopScan cancels scanning and sets isScanning false`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)
        assertTrue(scanner.isScanning.value)

        scanner.stopScan()

        assertFalse(scanner.isScanning.value)
        verify { leScanner.stopScan(callbackSlot.captured) }
    }

    @Test
    fun `setFilterUnknown true shows only named devices`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)

        callbackSlot.captured.onScanResult(1, mockDevice("AA:00:00:00:00:01", "KnownDevice", -50))
        callbackSlot.captured.onScanResult(1, mockDevice("AA:00:00:00:00:02", null, -70))

        assertEquals(2, scanner.scannedDevices.value.size)

        scanner.setFilterUnknown(true)

        assertEquals(1, scanner.scannedDevices.value.size)
        assertEquals("KnownDevice", scanner.scannedDevices.value[0].name)
    }

    @Test
    fun `setFilterUnknown false restores all discovered devices`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)

        callbackSlot.captured.onScanResult(1, mockDevice("AA:00:00:00:00:01", "KnownDevice", -50))
        callbackSlot.captured.onScanResult(1, mockDevice("AA:00:00:00:00:02", null, -70))

        scanner.setFilterUnknown(true)
        assertEquals(1, scanner.scannedDevices.value.size)

        scanner.setFilterUnknown(false)
        assertEquals(2, scanner.scannedDevices.value.size)
    }

    @Test
    fun `onScanFailed sets isScanning false`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)
        assertTrue(scanner.isScanning.value)

        callbackSlot.captured.onScanFailed(2)
        advanceUntilIdle()

        assertFalse(scanner.isScanning.value)
    }

    @Test
    fun `device retention across rescans retains older devices`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)
        callbackSlot.captured.onScanResult(1, mockDevice("11:11:11:11:11:11", "Device A", -50))
        scanner.stopScan()

        assertEquals(1, scanner.scannedDevices.value.size)

        scanner.startScan(backgroundScope)
        callbackSlot.captured.onScanResult(1, mockDevice("22:22:22:22:22:22", "Device B", -60))
        scanner.stopScan()

        assertEquals(2, scanner.scannedDevices.value.size)
        assertEquals("Device A", scanner.scannedDevices.value[0].name)
        assertEquals("Device B", scanner.scannedDevices.value[1].name)
    }

    @Test
    fun `toggle filterUnknown during active scan filters realtime discoveries`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)

        callbackSlot.captured.onScanResult(1, mockDevice("11:11:11:11:11:11", "Known", -50))
        assertEquals(1, scanner.scannedDevices.value.size)

        scanner.setFilterUnknown(true)
        callbackSlot.captured.onScanResult(1, mockDevice("22:22:22:22:22:22", null, -50))

        assertEquals(1, scanner.scannedDevices.value.size)

        scanner.setFilterUnknown(false)
        assertEquals(2, scanner.scannedDevices.value.size)
    }

    @Test
    fun `reset clears scanning flow and all device caches`() = runTest(testDispatcher) {
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        scanner.startScan(backgroundScope)
        callbackSlot.captured.onScanResult(1, mockDevice("11:11:11:11:11:11", "Device", -50))
        assertTrue(scanner.isScanning.value)

        scanner.reset()

        assertFalse(scanner.isScanning.value)
        assertEquals(0, scanner.scannedDevices.value.size)

        scanner.startScan(backgroundScope)
        assertEquals(0, scanner.scannedDevices.value.size)
    }

    @Test
    fun `filterUnknown stateflow emits correctly`() = runTest(testDispatcher) {
        val scanner = BleScanner(buildMockAdapter())
        
        assertFalse(scanner.filterUnknown.value)

        scanner.setFilterUnknown(true)
        assertTrue(scanner.filterUnknown.value)

        scanner.setFilterUnknown(false)
        assertFalse(scanner.filterUnknown.value)
    }
}
