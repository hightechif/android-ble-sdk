package com.edts.blesample.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edts.blesdk.core.BleConnection
import com.edts.blesdk.core.BleManager
import com.edts.blesdk.core.BleScanner
import com.edts.blesdk.core.ConnectionState
import com.edts.blesdk.model.BleDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var connectionJob: Job? = null
    private var notificationsJob: Job? = null

    private val bleScanner: BleScanner
        get() = bleManager.scanner

    // ─── Scanning state – delegated directly from the SDK scanner ───────────────
    val scannedDevices: StateFlow<List<BleDevice>> = bleScanner.scannedDevices
    val isScanning: StateFlow<Boolean> = bleScanner.isScanning
    val filterUnknown: StateFlow<Boolean> = bleScanner.filterUnknown

    // ─── Connection state ────────────────────────────────────────────────────────
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    private val _logs = MutableStateFlow("App started\n")
    val logs: StateFlow<String> = _logs.asStateFlow()

    init {
        viewModelScope.launch {
            // Keep track of previously logged devices to only log new ones
            var previousDevices = emptyList<BleDevice>()
            bleScanner.scannedDevices.collect { currentDevices ->
                if (currentDevices.size > previousDevices.size) {
                    val newDevices = currentDevices - previousDevices.toSet()
                    newDevices.forEach { device ->
                        log("Found: ${device.name} - ${device.macAddress}")
                    }
                }
                previousDevices = currentDevices
            }
        }
    }

    // ─── Scanning actions (delegated to SDK) ─────────────────────────────────────

    fun startScan() {
        log("Starting scan...")
        bleScanner.startScan(viewModelScope)
    }

    fun stopScan() {
        log("Scan manually stopped.")
        bleScanner.stopScan()
    }

    fun setFilterUnknown(filtered: Boolean) {
        log("Filter unknown devices: $filtered")
        bleScanner.setFilterUnknown(filtered)
    }


    // ─── Connection actions ──────────────────────────────────────────────────────

    fun connectToDevice(device: BleDevice) {
        log("Connecting to ${device.name}...")
        bleScanner.stopScan()
        bleConnection?.close()
        bleConnection = bleManager.connect(device)
        bleConnection?.connect()

        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            bleConnection?.connectionState?.collect { state ->
                log("Connection State: $state")
                val connected = state == ConnectionState.CONNECTED
                _isConnected.value = connected

                if (connected) {
                    _connectedDevice.value = device
                    log("Connected! Discovering services...")
                    val success = bleConnection?.discoverServices() ?: false
                    log(if (success) "Services discovered" else "Service discovery failed")

                    if (success) {
                        try {
                            log("Requesting MTU 512...")
                            bleConnection?.requestMtu(512)
                            log("MTU Negotiated successfully")
                        } catch (e: Exception) {
                            log("MTU Negotiation info: ${e.message}")
                        }

                        // Automatically read device info and battery once connected
                        try {
                            val manufacturer = bleConnection?.readDeviceManufacturerName()
                            if (manufacturer != null) log("Manufacturer: $manufacturer")
                        } catch (e: Exception) {
                            if (e.message == "Characteristic not found") {
                                log("Manufacturer info not supported by device")
                            } else {
                                log("Failed manufacturer read: ${e.message}")
                            }
                        }

                        try {
                            val battery = bleConnection?.readBatteryLevel()
                            if (battery != null) log("Battery Level: $battery%")
                        } catch (e: Exception) {
                            if (e.message == "Characteristic not found") {
                                log("Battery level not supported by device")
                            } else {
                                log("Failed battery read: ${e.message}")
                            }
                        }
                    }
                } else if (_connectedDevice.value?.macAddress == device.macAddress) {
                    _connectedDevice.value = null
                }
            }
        }

        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            bleConnection?.notifications?.collect { notification ->
                when (notification.charUuid) {
                    com.edts.blesdk.util.BleExtensions.HEART_RATE_MEASUREMENT_CHAR_UUID -> {
                        val hr =
                            com.edts.blesdk.util.BleExtensions.parseHeartRate(notification.data)
                        log("Notification Heart Rate: $hr bpm")
                    }

                    com.edts.blesdk.util.BleExtensions.BLOOD_PRESSURE_MEASUREMENT_CHAR_UUID -> {
                        val bp =
                            com.edts.blesdk.util.BleExtensions.parseBloodPressure(notification.data)
                        log("Notification Blood Pressure: ${bp?.systolic}/${bp?.diastolic} (MAP: ${bp?.map})")
                    }

                    com.edts.blesdk.util.BleExtensions.TEMPERATURE_MEASUREMENT_CHAR_UUID -> {
                        val temp =
                            com.edts.blesdk.util.BleExtensions.parseHealthThermometer(notification.data)
                        log("Notification Temperature: ${temp?.temperature} ${if (temp?.unitIsFahrenheit == true) "F" else "C"}")
                    }

                    com.edts.blesdk.util.BleExtensions.WEIGHT_MEASUREMENT_CHAR_UUID -> {
                        val weight =
                            com.edts.blesdk.util.BleExtensions.parseWeightMeasurement(notification.data)
                        log("Notification Weight: ${weight?.weight} ${if (weight?.unitIsLbs == true) "lbs" else "kg"}")
                    }

                    else -> {
                        log("Notification: ${notification.charUuid} -> ${notification.data.joinToString()}")
                    }
                }
            }
        }
    }

    fun subscribeToHeartRate() {
        log("Action: Enable HR notifications...")
        viewModelScope.launch {
            try {
                bleConnection?.subscribeToHeartRate()
                log("Action: Subscribed to Heart Rate")
            } catch (e: Exception) {
                log("Action failed: ${e.localizedMessage}")
            }
        }
    }

    fun subscribeToBloodPressure() {
        log("Action: Enable BP notifications...")
        viewModelScope.launch {
            try {
                bleConnection?.subscribeToBloodPressure()
                log("Action: Subscribed to Blood Pressure")
            } catch (e: Exception) {
                log("Action failed: ${e.localizedMessage}")
            }
        }
    }

    fun subscribeToThermometer() {
        log("Action: Enable Temp notifications...")
        viewModelScope.launch {
            try {
                bleConnection?.subscribeToHealthThermometer()
                log("Action: Subscribed to Thermometer")
            } catch (e: Exception) {
                log("Action failed: ${e.localizedMessage}")
            }
        }
    }

    fun subscribeToWeightScale() {
        log("Action: Enable Weight notifications...")
        viewModelScope.launch {
            try {
                bleConnection?.subscribeToWeightScale()
                log("Action: Subscribed to Weight Scale")
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
        _logs.value = "${_logs.value}$message\n"
    }

    override fun onCleared() {
        super.onCleared()
        bleConnection?.close()
    }
}