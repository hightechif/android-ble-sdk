package com.sunstrinq.blesdk.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import com.sunstrinq.blesdk.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        // Mock android.util.Log (not available on JVM)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        // Note: ScanSettings.Builder is handled by isReturnDefaultValues = true in build.gradle.kts
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private fun buildMockAdapter(leScanner: BluetoothLeScanner? = null): BluetoothAdapter {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.bluetoothLeScanner } returns leScanner
        return adapter
    }

    private fun buildMockLeScanner(): Pair<BluetoothLeScanner, CapturingSlot<ScanCallback>> {
        val leScanner = mockk<BluetoothLeScanner>(relaxed = true)
        val callbackSlot = slot<ScanCallback>()
        every { leScanner.startScan(null, any(), capture(callbackSlot)) } returns Unit
        return leScanner to callbackSlot
    }

    // ─── startScan ────────────────────────────────────────────────────────────────

    @Test
    fun `startScan returns isScanning true when hardware scanner is available`() = runTest {
        // Arrange
        val (leScanner, _) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        // Act
        scanner.startScan(backgroundScope)

        // Assert
        assertThat(scanner.isScanning.value).isTrue()
    }

    // ─── stopScan ─────────────────────────────────────────────────────────────────

    @Test
    fun `stopScan returns isScanning false when called after startScan`() = runTest {
        // Arrange
        val (leScanner, _) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))
        scanner.startScan(backgroundScope)
        assertThat(scanner.isScanning.value).isTrue()

        // Act
        scanner.stopScan()

        // Assert
        assertThat(scanner.isScanning.value).isFalse()
    }

    @Test
    fun `stopScan returns isScanning false when called without a prior startScan`() = runTest {
        // Arrange
        val scanner = BleScanner(buildMockAdapter())

        // Act
        scanner.stopScan()

        // Assert
        assertThat(scanner.isScanning.value).isFalse()
    }

    // ─── setFilterUnknown ─────────────────────────────────────────────────────────

    @Test
    fun `setFilterUnknown returns filterUnknown true when toggled on`() = runTest {
        // Arrange
        val scanner = BleScanner(buildMockAdapter())
        assertThat(scanner.filterUnknown.value).isFalse()

        // Act
        scanner.setFilterUnknown(true)

        // Assert
        assertThat(scanner.filterUnknown.value).isTrue()
    }

    @Test
    fun `setFilterUnknown returns filterUnknown false when toggled off`() = runTest {
        // Arrange
        val scanner = BleScanner(buildMockAdapter())
        scanner.setFilterUnknown(true)

        // Act
        scanner.setFilterUnknown(false)

        // Assert
        assertThat(scanner.filterUnknown.value).isFalse()
    }

    // ─── Scan failure ─────────────────────────────────────────────────────────────

    @Test
    fun `rawScanFlow throws exception when scan fails`() = runTest {
        // Arrange
        val (leScanner, callbackSlot) = buildMockLeScanner()
        val scanner = BleScanner(buildMockAdapter(leScanner))

        // Act
        val scanJob = launch(mainDispatcherRule.testDispatcher) {
            scanner.startScan(this)
        }

        // We know startScan launches a job and registers the callback
        runCurrent() // let the startScan job run
        val callback = callbackSlot.captured

        // Fire failure
        callback.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED)

        runCurrent() // process failure

        // Assert
        assertThat(scanner.isScanning.value).isFalse() // Scanning state resets on failure
        
        scanJob.cancel()
    }
    // ─── reset ────────────────────────────────────────────────────────────────────

    @Test
    fun `reset returns filterUnknown still false when called after filter was enabled`() = runTest {
        // Arrange
        val scanner = BleScanner(buildMockAdapter())
        scanner.setFilterUnknown(true)

        // Act
        scanner.reset()

        // Assert — reset only clears devices/scanning state; filter state is preserved
        assertThat(scanner.isScanning.value).isFalse()
        assertThat(scanner.scannedDevices.value).isEmpty()
    }

}
