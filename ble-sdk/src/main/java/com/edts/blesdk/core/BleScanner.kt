package com.edts.blesdk.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.edts.blesdk.model.BleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import android.util.Log

class BleScanner(private val bluetoothAdapter: BluetoothAdapter) {

    @SuppressLint("MissingPermission")
    fun scanForDevices(): Flow<BleDevice> = callbackFlow {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth LE Scanner not available"))
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val device = result.device
                val bleDevice = BleDevice(
                    name = device.name ?: "Unknown",
                    macAddress = device.address,
                    rssi = result.rssi,
                    device = device
                )
                trySend(bleDevice)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BleScanner", "Scan failed with error code: $errorCode")
                close(Exception("Scan failed with error code: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)

        awaitClose {
            scanner.stopScan(scanCallback)
        }
    }
}
