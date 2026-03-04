package com.edts.blesdk.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import com.edts.blesdk.model.BleDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BleScanner(private val bluetoothAdapter: BluetoothAdapter) {

    // ─── Internal state ──────────────────────────────────────────────────────────
    private val _rawDevices = MutableStateFlow<List<BleDevice>>(emptyList())

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _filterUnknown = MutableStateFlow(false)
    val filterUnknown: StateFlow<Boolean> = _filterUnknown.asStateFlow()

    private var scanJob: Job? = null

    // ─── Public API ──────────────────────────────────────────────────────────────

    fun startScan(scope: CoroutineScope) {
        recomputeFilteredList()
        startScanJob(scope)
    }

    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    fun setFilterUnknown(filtered: Boolean) {
        if (_filterUnknown.value != filtered) {
            _filterUnknown.value = filtered
            recomputeFilteredList()
        }
    }

    fun reset() {
        stopScan()
        _rawDevices.value = emptyList()
        _scannedDevices.value = emptyList()
    }

    // ─── Internal helpers ────────────────────────────────────────────────────────

    private fun recomputeFilteredList() {
        val filtered = if (_filterUnknown.value) {
            _rawDevices.value.filter { it.name != "Unknown" }
        } else {
            _rawDevices.value
        }
        _scannedDevices.value = filtered
    }

    private fun startScanJob(scope: CoroutineScope) {
        scanJob?.cancel()
        _isScanning.value = true

        scanJob = scope.launch {
            try {
                rawScanFlow().collect { device ->
                    val isNew = !_rawDevices.value.any { it.macAddress == device.macAddress }
                    if (isNew) {
                        _rawDevices.update { it + device }

                        val passesFilter = if (_filterUnknown.value) device.name != "Unknown" else true
                        if (passesFilter) {
                            _scannedDevices.update { it + device }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("BleScanner", "Scan error: ${e.message}")
            } finally {
                // Check if this job is still the active one before resetting state
                // This prevents race conditions where a new scan starts before the old one finishes cleanup
                if (scanJob?.isActive != true) {
                    _isScanning.value = false
                }
            }
        }
    }

    private fun rawScanFlow() = callbackFlow {
        val leScanner = bluetoothAdapter.bluetoothLeScanner
        if (leScanner == null) {
            close(IllegalStateException("Bluetooth LE Scanner not available"))
            return@callbackFlow
        }

        val seen = mutableSetOf<String>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (seen.add(device.address)) {
                    trySend(
                        BleDevice(
                            name = device.name ?: "Unknown",
                            macAddress = device.address,
                            rssi = result.rssi,
                            device = device
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleScanner", "Scan failed: $errorCode")
                close(Exception("Scan failed with error code: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            ?.build()

        leScanner.startScan(null, settings, callback)
        awaitClose { leScanner.stopScan(callback) }
    }
}
