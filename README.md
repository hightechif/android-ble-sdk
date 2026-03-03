# BleSmartwatchSdk

A complete Android Bluetooth Low Energy (BLE) SDK and robust sample application written entirely in Kotlin. This project focuses on providing a clean, stateful, and reactive API for interacting with BLE devices, with a specific architectural emphasis on separating core Bluetooth logic from UI components.

## Project Structure

The project is structured into two main modules to ensure a strict separation of concerns:

- **`ble-sdk`**: The core BLE library containing all the complex Bluetooth logic, scanning state management, connection handling, and message transactions.
- **`app`**: A sample application built with **Jetpack Compose** that consumes the `ble-sdk`. It strictly acts as a UI layer proxy, observing states and delegating user intents to the SDK.

---

## Features

### Stateful BLE Scanning (`BleScanner`)
- Uses Kotlin **Coroutines** and **StateFlow** to provide a reactive stream of discovered devices.
- Retains previously discovered devices during continuous scanning.
- Filters out "Unknown" nameless devices dynamically in real-time without needing to restart the hardware scan.

### Robust Connection Management (`BleConnection`)
- Fully manages GATT connections, service discovery, and characteristic communication.
- Exposes real-time connectivity states (`ConnectionState.CONNECTING`, `CONNECTED`, `DISCONNECTED`).
- Supports reading notifications, writing messages, reading RSSI directly from connected peripherals, and disabling notifications.
- Automatically retries connection attempts and provides descriptive logging.

### Architecture & Testability

- **MVVM Architecture**: The app's `MainViewModel` purely delegates to `BleScanner` and exposes flows, keeping the ViewModel extremely thin.

```ASCII
┌─────────────────────────────────┐
│  app module                     │
│  ┌─────────────────────────┐   │
│  │  MainViewModel (proxy)  │   │
│  │  startScan() ──────────────────────┐
│  │  stopScan() ───────────────────┐   │
│  │  setFilterUnknown() ───────┐   │   │
│  │  scannedDevices ←──────────┼───┼───┼──┐
│  └─────────────────────────┘  │   │   │  │
└───────────────────────────────┼───┼───┼──┼──┘
                                │   │   │  │
┌─────────────────────────────────────────────┐
│  ble-sdk module                 │   │   │  │
│  ┌──────────────────────────────▼───▼───▼──▼┐
│  │  BleScanner (stateful)                   │
│  │  _rawDevices, _scannedDevices            │
│  │  filterUnknown                           │
│  └──────────────────────────────────────────┘
└─────────────────────────────────────────────┘
```

- **Dependency Injection**: Integrated with Koin.
- **Unit Testing**: Excellent test coverage in both modules using **MockK**, **Robolectric**, and **Coroutines Test Dispatchers**.

---

## Getting Started

### Prerequisites

- Android Studio Giraffe or newer
- Minimum SDK: 24
- Target SDK: 34
- Kotlin 1.9+

### Permissions
The sample app automatically requests necessary permissions at runtime depending on the OS version (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`).

> **Note:** Ensure your physical test device has Bluetooth enabled before initiating a scan. The sample app will gracefully display an alert if it is off.

### Usage Example

To start a scan and observe devices from your ViewModel:

```kotlin
val bleScanner = BleManager(applicationContext).scanner

// Observe the filtered device list in real-time
val scannedDevices = bleScanner.scannedDevices

// Start continuous background scanning
bleScanner.startScan(viewModelScope)

// Connect to a specific BLE device
val connection = bleManager.connect(device)
connection.discoverServices()
```

## Testing

The project is thoroughly tested to ensure thread-safety and proper state propagation across hardware callbacks.

Run tests using Gradle:
```bash
./gradlew test
```

Tests handle eager coroutine dispatchers to accurately capture and verify standard `BluetoothAdapter` and `GATT` callbacks.

## License

This project is open-source and intended for educational and reference purposes regarding correct Android BLE architecture.
