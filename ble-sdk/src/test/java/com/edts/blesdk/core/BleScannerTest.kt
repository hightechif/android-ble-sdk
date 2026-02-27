package com.edts.blesdk.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BleScannerTest {

    @Test
    fun scanForDevices_scannerNotAvailable_throwsException() = runTest {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.bluetoothLeScanner } returns null

        val scanner = BleScanner(adapter)

        try {
            scanner.scanForDevices().first()
            assert(false) { "Should have thrown exception" }
        } catch (e: Exception) {
            assertEquals("Bluetooth LE Scanner not available", e.message)
        }
    }

    @Test
    fun scanForDevices_emitsUniqueDevices() = runTest {
        val adapter = mockk<BluetoothAdapter>()
        val leScanner = mockk<BluetoothLeScanner>(relaxed = true)
        every { adapter.bluetoothLeScanner } returns leScanner

        val scanner = BleScanner(adapter)

        val callbackSlot = slot<ScanCallback>()
        every { leScanner.startScan(any(), any(), capture(callbackSlot)) } returns Unit

        val results = mutableListOf<com.edts.blesdk.model.BleDevice>()
        val job = launch {
            scanner.scanForDevices().take(2).toList(results)
        }

        // Wait to make sure flow has started collecting and callback is captured
        kotlinx.coroutines.delay(100)

        // Mock devices and results
        val device1 = mockk<BluetoothDevice>()
        every { device1.address } returns "00:11:22:33:44:55"
        every { device1.name } returns "Device1"

        val scanResult1 = mockk<ScanResult>()
        every { scanResult1.device } returns device1
        every { scanResult1.rssi } returns -50

        val device2 = mockk<BluetoothDevice>()
        every { device2.address } returns "66:77:88:99:AA:BB"
        every { device2.name } returns "Device2"

        val scanResult2 = mockk<ScanResult>()
        every { scanResult2.device } returns device2
        every { scanResult2.rssi } returns -60

        // Simulate incoming scan results
        callbackSlot.captured.onScanResult(1, scanResult1)
        // Duplicate device, should be ignored by the scanner logic
        callbackSlot.captured.onScanResult(1, scanResult1)
        callbackSlot.captured.onScanResult(1, scanResult2)

        job.join()

        assertEquals(2, results.size)
        assertEquals("Device1", results[0].name)
        assertEquals("00:11:22:33:44:55", results[0].macAddress)
        assertEquals(-50, results[0].rssi)

        assertEquals("Device2", results[1].name)
        assertEquals("66:77:88:99:AA:BB", results[1].macAddress)
        assertEquals(-60, results[1].rssi)
        
        verify { leScanner.stopScan(any<ScanCallback>()) }
    }

    @Test
    fun scanForDevices_onScanFailed_throwsException() = runTest {
        val adapter = mockk<BluetoothAdapter>()
        val leScanner = mockk<BluetoothLeScanner>(relaxed = true)
        every { adapter.bluetoothLeScanner } returns leScanner

        val scanner = BleScanner(adapter)

        val callbackSlot = slot<ScanCallback>()
        every { leScanner.startScan(any(), any(), capture(callbackSlot)) } returns Unit

        val job = launch {
            try {
                scanner.scanForDevices().first()
                assert(false) { "Should have thrown exception" }
            } catch (e: Exception) {
                assertEquals("Scan failed with error code: 2", e.message)
            }
        }

        kotlinx.coroutines.delay(100)
        callbackSlot.captured.onScanFailed(2)

        job.join()
    }
}
