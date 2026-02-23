package com.edts.blesdk.core

import android.content.Context
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import com.edts.blesdk.model.BleDevice

class BleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager by lazy {
        context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    val scanner: BleScanner by lazy {
        if (bluetoothAdapter == null) {
            throw IllegalStateException("Bluetooth is not supported on this device")
        }
        BleScanner(bluetoothAdapter!!)
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun connect(device: BleDevice): BleConnection {
        return BleConnection(context, device.device)
    }
}
