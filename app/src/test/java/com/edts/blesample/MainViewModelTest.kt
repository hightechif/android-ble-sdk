package com.edts.blesample

import android.app.Application
import com.edts.blesdk.core.BleConnection
import com.edts.blesdk.core.BleManager
import com.edts.blesdk.core.BleScanner
import com.edts.blesdk.core.ConnectionState
import com.edts.blesdk.model.BleDevice
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var application: Application
    private lateinit var bleManager: BleManager
    private lateinit var bleScanner: BleScanner
    private lateinit var bleConnection: BleConnection
    private val testDispatcher = StandardTestDispatcher()

    private val fakeScannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    private val fakeIsScanning = MutableStateFlow(false)
    private val fakeFilterUnknown = MutableStateFlow(false)

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        bleManager = mockk(relaxed = true)
        bleScanner = mockk(relaxed = true)
        bleConnection = mockk(relaxed = true)

        every { bleManager.scanner } returns bleScanner
        every { bleScanner.scannedDevices } returns fakeScannedDevices
        every { bleScanner.isScanning } returns fakeIsScanning
        every { bleScanner.filterUnknown } returns fakeFilterUnknown

        viewModel = MainViewModel(application, bleManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startScan delegates to bleScanner`() = runTest {
        viewModel.startScan()
        advanceUntilIdle()

        verify { bleScanner.startScan(any()) }
        assertTrue(viewModel.logs.value.contains("Starting scan"))
    }

    @Test
    fun `stopScan delegates to bleScanner`() = runTest {
        viewModel.stopScan()
        advanceUntilIdle()

        verify { bleScanner.stopScan() }
        assertTrue(viewModel.logs.value.contains("Scan manually stopped"))
    }

    @Test
    fun `setFilterUnknown delegates to bleScanner`() = runTest {
        viewModel.setFilterUnknown(true)
        advanceUntilIdle()

        verify { bleScanner.setFilterUnknown(true) }
        assertTrue(viewModel.logs.value.contains("Filter unknown devices: true"))
    }



    @Test
    fun `scannedDevices reflects scanner state flow`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        fakeScannedDevices.value = listOf(device)

        assertEquals(1, viewModel.scannedDevices.value.size)
        assertEquals("TestDevice", viewModel.scannedDevices.value[0].name)
    }

    @Test
    fun `connectToDevice updates connection state on CONNECTED`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection

        val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()

        viewModel.connectToDevice(device)
        advanceUntilIdle()

        assertEquals(false, viewModel.isConnected.value)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()

        assertEquals(true, viewModel.isConnected.value)
        assertTrue(viewModel.logs.value.contains("Connected! Discovering services..."))
        coVerify { bleConnection.discoverServices() }
    }

    @Test
    fun `readNotification logs the action request`() = runTest {
        viewModel.readNotification()
        advanceUntilIdle()

        assertTrue(viewModel.logs.value.contains("Action: Read Notification request..."))
    }

    @Test
    fun `writeMessage logs the action request`() = runTest {
        viewModel.writeMessage()
        advanceUntilIdle()

        assertTrue(viewModel.logs.value.contains("Action: Write Message request..."))
    }
}
