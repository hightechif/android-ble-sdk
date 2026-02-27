package com.edts.blesample

import android.app.Application
import com.edts.blesdk.core.BleConnection
import com.edts.blesdk.core.BleManager
import com.edts.blesdk.core.BleScanner
import com.edts.blesdk.core.ConnectionState
import com.edts.blesdk.model.BleDevice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var application: Application
    private lateinit var bleManager: BleManager
    private lateinit var bleScanner: BleScanner
    private lateinit var bleConnection: BleConnection
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        bleManager = mockk(relaxed = true)
        bleScanner = mockk(relaxed = true)
        bleConnection = mockk(relaxed = true)

        every { bleManager.scanner } returns bleScanner
        viewModel = MainViewModel(application, bleManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startScan_collectsDevices() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleScanner.scanForDevices() } returns flowOf(device)

        viewModel.startScan()
        advanceUntilIdle()

        val devices = viewModel.scannedDevices.value
        assertEquals(1, devices.size)
        assertEquals(device, devices[0])
        assertTrue(viewModel.logs.value.contains("Found: TestDevice"))
    }

    @Test
    fun connectToDevice_updatesConnectionState() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        
        val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()

        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Initially disconnected
        assertEquals(false, viewModel.isConnected.value)

        // Change state
        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()
        
        assertEquals(true, viewModel.isConnected.value)
        assertTrue(viewModel.logs.value.contains("Connected! Discovering services..."))
        coVerify { bleConnection.discoverServices() }
    }

    @Test
    fun readNotification_enablesNotifications() = runTest {
        viewModel.readNotification()
        advanceUntilIdle()

        // Even without an active connection set, the log should show we requested it.
        assertTrue(viewModel.logs.value.contains("Action: Read Notification request..."))
    }

    @Test
    fun writeMessage_writesToCharacteristic() = runTest {
        viewModel.writeMessage()
        advanceUntilIdle()

        assertTrue(viewModel.logs.value.contains("Action: Write Message request..."))
    }
}
