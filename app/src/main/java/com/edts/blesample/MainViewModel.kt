package com.edts.blesample

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edts.blesdk.core.BleConnection
import com.edts.blesdk.core.BleManager
import com.edts.blesdk.core.ConnectionState
import com.edts.blesdk.model.BleDevice
import com.edts.blesdk.util.BleExtensions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)
    private var bleConnection: BleConnection? = null
    
    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    private var notificationsJob: Job? = null

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

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
                    // Add distinct devices safely
                    _scannedDevices.update { currentList ->
                        if (currentList.none { it.macAddress == device.macAddress }) {
                             currentList + device
                        } else {
                             currentList
                        }
                    }
                }
            } catch (e: Exception) {
                log("Scan error: ${e.message}")
            }
        }
    }

    fun connectToDevice(device: BleDevice) {
        log("Connecting to ${device.name}...")
        bleConnection?.close()
        bleConnection = bleManager.connect(device)
        bleConnection?.connect()

        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            bleConnection?.connectionState?.collect { state ->
                log("Connection State:Async $state")
                _isConnected.value = state == ConnectionState.CONNECTED
                
                if (state == ConnectionState.CONNECTED) {
                    log("Connected! Discovering services...")
                    val success = bleConnection?.discoverServices() ?: false
                    if (success) {
                        log("Services discovered")
                    } else {
                         log("Service discovery failed")
                    }
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

    private fun log(message: String) {
        val currentLog = _logs.value
        _logs.value = "$currentLog$message\n"
    }
    
    override fun onCleared() {
        super.onCleared()
        bleConnection?.close()
    }
}
