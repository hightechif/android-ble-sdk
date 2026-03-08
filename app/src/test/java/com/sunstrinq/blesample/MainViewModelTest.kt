package com.sunstrinq.blesample

import android.app.Application
import com.sunstrinq.blesample.ui.MainViewModel
import com.sunstrinq.blesdk.core.BleConnection
import com.sunstrinq.blesdk.core.BleManager
import com.sunstrinq.blesdk.core.BleScanner
import com.sunstrinq.blesdk.model.ConnectionState
import com.sunstrinq.blesdk.model.BleDevice
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.sunstrinq.blesdk.constant.BleConstants
import com.sunstrinq.blesdk.model.BleNotification
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var bleManager: BleManager
    private lateinit var bleScanner: BleScanner
    private lateinit var bleConnection: BleConnection
    private lateinit var viewModel: MainViewModel

    private val fakeScannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    private val fakeIsScanning = MutableStateFlow(false)
    private val fakeFilterUnknown = MutableStateFlow(false)

    @Before
    fun setUp() {
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
        unmockkAll()
    }

    // ─── startScan ────────────────────────────────────────────────────────────────

    @Test
    fun `startScan returns scan delegated when called`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.startScan()
        advanceUntilIdle()

        // Assert
        verify { bleScanner.startScan(any()) }
    }

    @Test
    fun `startScan returns scan log appended when called`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.startScan()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Starting scan")
    }

    // ─── stopScan ─────────────────────────────────────────────────────────────────

    @Test
    fun `stopScan returns scan stopped when called`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.stopScan()
        advanceUntilIdle()

        // Assert
        verify { bleScanner.stopScan() }
    }

    @Test
    fun `stopScan returns stop log appended when called`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.stopScan()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Scan manually stopped")
    }

    // ─── setFilterUnknown ─────────────────────────────────────────────────────────

    @Test
    fun `setFilterUnknown returns filter delegated when toggled true`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.setFilterUnknown(true)
        advanceUntilIdle()

        // Assert
        verify { bleScanner.setFilterUnknown(true) }
    }

    @Test
    fun `setFilterUnknown returns filter delegated when toggled false`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.setFilterUnknown(false)
        advanceUntilIdle()

        // Assert
        verify { bleScanner.setFilterUnknown(false) }
    }

    @Test
    fun `setFilterUnknown returns filter log appended when toggled`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.setFilterUnknown(true)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Filter unknown devices: true")
    }

    // ─── scannedDevices ───────────────────────────────────────────────────────────

    @Test
    fun `scannedDevices returns reflected list when scanner emits devices`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())

        // Act
        fakeScannedDevices.value = listOf(device)

        // Assert
        assertThat(viewModel.scannedDevices.value).hasSize(1)
        assertThat(viewModel.scannedDevices.value[0].name).isEqualTo("TestDevice")
    }

    @Test
    fun `scannedDevices returns empty list when scanner emits no devices`() = runTest {
        // Arrange — fakeScannedDevices starts empty

        // Act — no emission

        // Assert
        assertThat(viewModel.scannedDevices.value).isEmpty()
    }

    @Test
    fun `scannedDevices returns found device log appended when new device emitted`() = runTest {
        // Arrange
        val device = BleDevice("HeartMonitor", "AA:BB:CC:DD:EE:FF", -65, mockk())

        // Act
        fakeScannedDevices.value = listOf(device)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Found: HeartMonitor - AA:BB:CC:DD:EE:FF")
    }

    // ─── connectToDevice ──────────────────────────────────────────────────────────

    @Test
    fun `connectToDevice returns isConnected false when state is DISCONNECTED`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.notifications } returns flowOf()

        // Act
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.isConnected.value).isFalse()
    }

    @Test
    fun `connectToDevice returns isConnected true when state transitions to CONNECTED`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.discoverServices() } returns true
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.isConnected.value).isTrue()
    }

    @Test
    fun `connectToDevice returns connectedDevice set when state becomes CONNECTED`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.discoverServices() } returns true
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.connectedDevice.value).isEqualTo(device)
    }

    @Test
    fun `connectToDevice returns services discovered when connection reaches CONNECTED`() =
        runTest {
            // Arrange
            val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
            every { bleManager.connect(device) } returns bleConnection
            val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
            every { bleConnection.connectionState } returns connectionStateFlow
            every { bleConnection.notifications } returns flowOf()
            coEvery { bleConnection.discoverServices() } returns true
            viewModel.connectToDevice(device)
            advanceUntilIdle()

            // Act
            connectionStateFlow.value = ConnectionState.CONNECTED
            advanceUntilIdle()

            // Assert
            coVerify { bleConnection.discoverServices() }
            assertThat(viewModel.logs.value).contains("Connected! Discovering services...")
        }

    @Test
    fun `connectToDevice returns isConnected false and connectedDevice null when disconnected`() =
        runTest {
            // Arrange
            val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
            every { bleManager.connect(device) } returns bleConnection
            val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
            every { bleConnection.connectionState } returns connectionStateFlow
            every { bleConnection.notifications } returns flowOf()
            coEvery { bleConnection.discoverServices() } returns true
            viewModel.connectToDevice(device)
            advanceUntilIdle()
            connectionStateFlow.value = ConnectionState.CONNECTED
            advanceUntilIdle()
            assertThat(viewModel.isConnected.value).isTrue()

            // Act
            connectionStateFlow.value = ConnectionState.DISCONNECTED
            advanceUntilIdle()

            // Assert
            assertThat(viewModel.isConnected.value).isFalse()
            assertThat(viewModel.connectedDevice.value).isNull()
        }

    @Test
    fun `connectToDevice returns connect log appended when called`() = runTest {
        // Arrange
        val device = BleDevice("MyWatch", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.notifications } returns flowOf()

        // Act
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Connecting to MyWatch...")
    }

    @Test
    fun `connectToDevice auto reads manufacturer and battery successfully`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.discoverServices() } returns true
        coEvery { bleConnection.requestMtu(512) } returns Unit
        coEvery { bleConnection.readDeviceManufacturerName() } returns "TestManufacturer"
        coEvery { bleConnection.readBatteryLevel() } returns 99

        viewModel.connectToDevice(device)
        advanceUntilIdle()
        
        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()

        assertThat(viewModel.logs.value).contains("Manufacturer: TestManufacturer")
        assertThat(viewModel.logs.value).contains("Battery Level: 99%")
    }

    @Test
    fun `connectToDevice handles manufacturer and battery read failures`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.discoverServices() } returns true
        coEvery { bleConnection.requestMtu(512) } throws RuntimeException("MTU Error")
        coEvery { bleConnection.readDeviceManufacturerName() } throws RuntimeException("Characteristic not found")
        coEvery { bleConnection.readBatteryLevel() } throws RuntimeException("Battery error")

        viewModel.connectToDevice(device)
        advanceUntilIdle()
        
        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()

        assertThat(viewModel.logs.value).contains("MTU Negotiation info: MTU Error")
        assertThat(viewModel.logs.value).contains("Manufacturer info not supported by device")
        assertThat(viewModel.logs.value).contains("Failed battery read: Battery error")
    }

    @Test
    fun `connectToDevice processes heart rate notifications`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        
        val notificationFlow = MutableStateFlow(BleNotification(UUID.randomUUID(), BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID, byteArrayOf(0x00, 0x4B)))
        every { bleConnection.notifications } returns notificationFlow
        coEvery { bleConnection.discoverServices() } returns true

        viewModel.connectToDevice(device)
        advanceUntilIdle()

        assertThat(viewModel.logs.value).contains("Notification Heart Rate: 75 bpm")
    }

    @Test
    fun `connectToDevice processes blood pressure notifications`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        
        val bpBytes = byteArrayOf(0x00, 120, 0, 80, 0, 90, 0)
        val notificationFlow = MutableStateFlow(BleNotification(UUID.randomUUID(), BleConstants.BLOOD_PRESSURE_MEASUREMENT_CHAR_UUID, bpBytes))
        every { bleConnection.notifications } returns notificationFlow
        coEvery { bleConnection.discoverServices() } returns true

        viewModel.connectToDevice(device)
        advanceUntilIdle()

        assertThat(viewModel.logs.value).contains("Notification Blood Pressure:")
    }
    
    @Test
    fun `connectToDevice processes temperature notifications`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        
        val tempBytes = byteArrayOf(0x00, 36, 0, 0, 0) 
        val notificationFlow = MutableStateFlow(BleNotification(UUID.randomUUID(), BleConstants.TEMPERATURE_MEASUREMENT_CHAR_UUID, tempBytes))
        every { bleConnection.notifications } returns notificationFlow
        coEvery { bleConnection.discoverServices() } returns true

        viewModel.connectToDevice(device)
        advanceUntilIdle()

        assertThat(viewModel.logs.value).contains("Notification Temperature:")
    }

    @Test
    fun `connectToDevice processes weight notifications`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        
        val weightBytes = byteArrayOf(0x00, 70, 0)
        val notificationFlow = MutableStateFlow(BleNotification(UUID.randomUUID(), BleConstants.WEIGHT_MEASUREMENT_CHAR_UUID, weightBytes))
        every { bleConnection.notifications } returns notificationFlow
        coEvery { bleConnection.discoverServices() } returns true

        viewModel.connectToDevice(device)
        advanceUntilIdle()

        assertThat(viewModel.logs.value).contains("Notification Weight:")
    }

    @Test
    fun `connectToDevice processes unknown notifications`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        
        val unknownChar = UUID.randomUUID()
        val notificationFlow = MutableStateFlow(BleNotification(UUID.randomUUID(), unknownChar, byteArrayOf(0x01, 0x02)))
        every { bleConnection.notifications } returns notificationFlow
        coEvery { bleConnection.discoverServices() } returns true

        viewModel.connectToDevice(device)
        advanceUntilIdle()

        assertThat(viewModel.logs.value).contains("Notification: $unknownChar -> 1, 2")
    }

    // ─── subscribeToHeartRate ─────────────────────────────────────────────────────────

    @Test
    fun `subscribeToHeartRate returns log appended when called`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.subscribeToHeartRate()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action: Enable HR notifications...")
    }

    @Test
    fun `subscribeToHeartRate returns success log when subscribeToHeartRate succeeds`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.subscribeToHeartRate() } returns Unit
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.subscribeToHeartRate()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action: Subscribed to Heart Rate")
    }

    @Test
    fun `subscribeToHeartRate returns error log when subscribeToHeartRate throws exception`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery {
            bleConnection.subscribeToHeartRate()
        } throws RuntimeException("BLE error")
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.subscribeToHeartRate()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action failed")
    }

    // ─── subscribeToBloodPressure ──────────────────────────────────────────────────

    @Test
    fun `subscribeToBloodPressure returns log appended when called`() = runTest {
        viewModel.subscribeToBloodPressure()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action: Enable BP notifications...")
    }

    @Test
    fun `subscribeToBloodPressure returns success log when succeeds`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.subscribeToBloodPressure() } returns Unit
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        viewModel.subscribeToBloodPressure()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action: Subscribed to Blood Pressure")
    }

    @Test
    fun `subscribeToBloodPressure returns error log when throws exception`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.subscribeToBloodPressure() } throws RuntimeException("BLE error")
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        viewModel.subscribeToBloodPressure()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action failed")
    }

    // ─── subscribeToThermometer ────────────────────────────────────────────────────

    @Test
    fun `subscribeToThermometer returns log appended when called`() = runTest {
        viewModel.subscribeToThermometer()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action: Enable Temp notifications...")
    }

    @Test
    fun `subscribeToThermometer returns success log when succeeds`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.subscribeToHealthThermometer() } returns Unit
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        viewModel.subscribeToThermometer()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action: Subscribed to Thermometer")
    }

    @Test
    fun `subscribeToThermometer returns error log when throws exception`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.subscribeToHealthThermometer() } throws RuntimeException("BLE error")
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        viewModel.subscribeToThermometer()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action failed")
    }
    
    // ─── subscribeToWeightScale ────────────────────────────────────────────────────

    @Test
    fun `subscribeToWeightScale returns log appended when called`() = runTest {
        viewModel.subscribeToWeightScale()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action: Enable Weight notifications...")
    }

    @Test
    fun `subscribeToWeightScale returns success log when succeeds`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.subscribeToWeightScale() } returns Unit
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        viewModel.subscribeToWeightScale()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action: Subscribed to Weight Scale")
    }

    @Test
    fun `subscribeToWeightScale returns error log when throws exception`() = runTest {
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.subscribeToWeightScale() } throws RuntimeException("BLE error")
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        viewModel.subscribeToWeightScale()
        advanceUntilIdle()
        assertThat(viewModel.logs.value).contains("Action failed")
    }

    // ─── readRssi ─────────────────────────────────────────────────────────────────

    @Test
    fun `readRssi returns log appended when called`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.readRssi()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action: Read RSSI request...")
    }

    @Test
    fun `readRssi returns rssi value log when readRssi succeeds`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.readRssi() } returns -72
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.readRssi()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action: RSSI is -72 dBm")
    }

    @Test
    fun `readRssi returns error log when readRssi throws exception`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.readRssi() } throws RuntimeException("RSSI read failed")
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.readRssi()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action failed")
    }

    // ─── writeDummyData ──────────────────────────────────────────────────────────

    @Test
    fun `writeDummyData returns success log when writeCharacteristic succeeds`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.writeCharacteristic(any(), any(), any()) } returns Unit
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.writeDummyData()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action: Write successful")
    }

    @Test
    fun `writeDummyData returns error log when writeCharacteristic throws exception`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.writeCharacteristic(any(), any(), any()) } throws RuntimeException("Write failed")
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.writeDummyData()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action failed")
    }

    // ─── disableHeartRate ────────────────────────────────────────────────────────

    @Test
    fun `disableHeartRate returns success log when disableNotifications succeeds`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.disableNotifications(any(), any()) } returns Unit
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.disableHeartRate()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action: HR notifications disabled")
    }

    @Test
    fun `disableHeartRate returns error log when disableNotifications throws exception`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        every { bleConnection.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.disableNotifications(any(), any()) } throws RuntimeException("Disable failed")
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.disableHeartRate()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action failed")
    }

    // ─── disconnect ───────────────────────────────────────────────────────────────

    @Test
    fun `disconnect returns isConnected false when called`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        val connectionStateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.discoverServices() } returns true
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.disconnect()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.isConnected.value).isFalse()
    }

    @Test
    fun `disconnect returns connectedDevice null when called`() = runTest {
        // Arrange
        val device = BleDevice("TestDevice", "00:11:22:33:44:55", -50, mockk())
        every { bleManager.connect(device) } returns bleConnection
        val connectionStateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        every { bleConnection.connectionState } returns connectionStateFlow
        every { bleConnection.notifications } returns flowOf()
        coEvery { bleConnection.discoverServices() } returns true
        viewModel.connectToDevice(device)
        advanceUntilIdle()

        // Act
        viewModel.disconnect()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.connectedDevice.value).isNull()
    }

    @Test
    fun `disconnect returns disconnect log appended when called`() = runTest {
        // Arrange — ViewModel already initialized in setUp

        // Act
        viewModel.disconnect()
        advanceUntilIdle()

        // Assert
        assertThat(viewModel.logs.value).contains("Action: Disconnect request...")
    }
}
