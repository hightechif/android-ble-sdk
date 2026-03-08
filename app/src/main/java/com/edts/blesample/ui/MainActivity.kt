package com.edts.blesample.ui

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.edts.blesample.ui.screen.ScanScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            viewModel.startScan()
        } else {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scannedDevices by viewModel.scannedDevices.collectAsState()
            val isConnected by viewModel.isConnected.collectAsState()
            val isScanning by viewModel.isScanning.collectAsState()
            val filterUnknown by viewModel.filterUnknown.collectAsState()
            val connectedDevice by viewModel.connectedDevice.collectAsState()
            val logs by viewModel.logs.collectAsState()

            ScanScreen(
                scannedDevices = scannedDevices,
                isConnected = isConnected,
                isScanning = isScanning,
                filterUnknown = filterUnknown,
                connectedDevice = connectedDevice,
                logs = logs,
                onFilterUnknownChange = { viewModel.setFilterUnknown(it) },
                onScanClick = {
                    if (checkPermissions()) {
                        val bluetoothManager =
                            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                        val bluetoothAdapter = bluetoothManager.adapter
                        if (bluetoothAdapter?.isEnabled == true) {
                            viewModel.startScan()
                        } else {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Bluetooth is Off")
                                .setMessage("Please turn on your phone's Bluetooth feature to scan for devices.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                },
                onStopScanClick = {
                    viewModel.stopScan()
                },
                onConnectClick = { device ->
                    viewModel.connectToDevice(device)
                },
                onDisconnectClick = {
                    viewModel.disconnect()
                },
                onSubscribeHeartRateClick = { viewModel.subscribeToHeartRate() },
                onSubscribeBloodPressureClick = { viewModel.subscribeToBloodPressure() },
                onSubscribeThermometerClick = { viewModel.subscribeToThermometer() },
                onSubscribeWeightScaleClick = { viewModel.subscribeToWeightScale() },
                onReadRssiClick = { viewModel.readRssi() },
                onWriteDummyClick = { viewModel.writeDummyData() },
                onDisableHeartRateClick = { viewModel.disableHeartRate() }
            )
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return false
        }
        return true
    }
}