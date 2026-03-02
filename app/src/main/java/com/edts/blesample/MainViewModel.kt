package com.edts.blesample

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edts.blesdk.core.BleConnection
import com.edts.blesdk.core.BleManager
import com.edts.blesdk.core.ConnectionState
import com.edts.blesdk.model.BleDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(
    application: Application,
    private val bleManager: BleManager
) : AndroidViewModel(application) {

    // Dummy UUIDs for demonstration purposes
    private val DUMMY_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val DUMMY_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    private var bleConnection: BleConnection? = null

    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    private var notificationsJob: Job? = null

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    private val _logs = MutableStateFlow("App started\n")
    val logs: StateFlow<String> = _logs.asStateFlow()

    fun startScan() {
        log("Starting scan...")
        // Clear previous results
        _scannedDevices.value = emptyList()
        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            try {
                bleManager.scanner.scanForDevices().collect { device ->
                    log("Found: ${device.name} - ${device.macAddress}")
                    _scannedDevices.update { it + device }
                }
            } catch (e: Exception) {
                log("Scan error: ${e.message}")
            }
        }
    }

    fun connectToDevice(device: BleDevice) {
        log("Connecting to ${device.name}...")
        scanJob?.cancel()
        bleConnection?.close()
        bleConnection = bleManager.connect(device)
        bleConnection?.connect()

        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            bleConnection?.connectionState?.collect { state ->
                log("Connection State:Async $state")
                val connected = state == ConnectionState.CONNECTED
                _isConnected.value = connected
                
                if (connected) {
                    _connectedDevice.value = device
                    log("Connected! Discovering services...")
                    val success = bleConnection?.discoverServices() ?: false
                    if (success) {
                        log("Services discovered")
                    } else {
                        log("Service discovery failed")
                    }
                } else if (_connectedDevice.value?.macAddress == device.macAddress) {
                    _connectedDevice.value = null
                }
            }
        }

        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            bleConnection?.notifications?.collect { notification ->
                log("Notification: ${notification.charUuid} -> ${notification.data.joinToString()}")
            }
        }
    }

    fun readNotification() {
        log("Action: Read Notification request...")
        viewModelScope.launch {
            try {
                bleConnection?.enableNotifications(DUMMY_SERVICE_UUID, DUMMY_CHAR_UUID)
                log("Action: Notifications enabled for dummy char")
            } catch (e: Exception) {
                log("Action failed: ${e.localizedMessage}")
            }
        }
    }

    fun writeMessage() {
        log("Action: Write Message request...")
        viewModelScope.launch {
            try {
                val demoBytes = "Hello".toByteArray()
                bleConnection?.writeCharacteristic(DUMMY_SERVICE_UUID, DUMMY_CHAR_UUID, demoBytes)
                log("Action: Message written")
            } catch (e: Exception) {
                log("Action failed: ${e.localizedMessage}")
            }
        }
    }

    fun disableNotification() {
        log("Action: Disable Notification request...")
        viewModelScope.launch {
            try {
                bleConnection?.disableNotifications(DUMMY_SERVICE_UUID, DUMMY_CHAR_UUID)
                log("Action: Notifications disabled")
            } catch (e: Exception) {
                log("Action failed: ${e.localizedMessage}")
            }
        }
    }

    fun readRssi() {
        log("Action: Read RSSI request...")
        viewModelScope.launch {
            try {
                val rssi = bleConnection?.readRssi()
                log("Action: RSSI is $rssi dBm")
            } catch (e: Exception) {
                log("Action failed: ${e.localizedMessage}")
            }
        }
    }

    fun disconnect() {
        log("Action: Disconnect request...")
        bleConnection?.close()
        _isConnected.value = false
        _connectedDevice.value = null
    }

    private fun log(message: String) {
        val currentLog = _logs.value
        _logs.value = "$currentLog$message\n"
    }

    override fun onCleared() {
        super.onCleared()
        bleConnection?.close()
    }
}
