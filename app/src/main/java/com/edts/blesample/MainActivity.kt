package com.edts.blesample

import android.Manifest
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
            val connectedDevice by viewModel.connectedDevice.collectAsState()
            val logs by viewModel.logs.collectAsState()

            ScanScreen(
                scannedDevices = scannedDevices,
                isConnected = isConnected,
                connectedDevice = connectedDevice,
                logs = logs,
                onScanClick = {
                    if (checkPermissions()) {
                        viewModel.startScan()
                    }
                },
                onConnectClick = { device ->
                    viewModel.connectToDevice(device)
                },
                onDisconnectClick = {
                    viewModel.disconnect()
                },
                onReadNotificationClick = { viewModel.readNotification() },
                onWriteMessageClick = { viewModel.writeMessage() },
                onDisableNotificationClick = { viewModel.disableNotification() },
                onReadRssiClick = { viewModel.readRssi() }
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
