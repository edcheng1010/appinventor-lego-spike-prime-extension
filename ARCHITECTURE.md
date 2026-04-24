# LEGO SPIKE Prime App Inventor Extension - Architecture Document

## 1. Project Overview
This project is an MIT App Inventor extension designed to provide reliable, intuitive Bluetooth Low Energy (BLE) communication with LEGO SPIKE Prime hubs. It is built specifically for educational environments (classrooms, maker spaces) where multiple hubs operate simultaneously and reliability is paramount.

## 2. Current Architectural State (The "Split")
The codebase currently contains two distinct architectural approaches. **Claude Code must understand this split before making any changes.**

### 2.1 The Active Architecture (MVP)
This is the working, self-contained implementation that must be used for the MVP.
- **Location:** `io.github.appinventor.legospikeprime` package
- **Files:** `LegoSpikePrime.java`, `BluetoothInterfaceImpl.java`
- **Status:** Fully functional, tested, and stable.
- **Characteristics:** Uses the App Inventor `BluetoothLE` component for underlying communication. Handles its own device list, RSSI staleness, and basic command sending.

### 2.2 The Planned Architecture (Future Extensibility)
This is a more sophisticated protocol implementation that was developed but **not yet integrated** into the main extension.
- **Location:** `io.github.appinventor.legospike` package
- **Files:** 13 helper classes (`SpikeProtocol.java`, `COBSEncoder.java`, `MessageHandler.java`, etc.)
- **Status:** Code complete but disconnected from the main extension.
- **Purpose:** Designed to handle the full complexity of the SPIKE Prime protocol (COBS encoding, CRC32, message parsing) and to allow future extensibility to other LEGO hubs.

**Rule for Claude Code:** For immediate bug fixes or minor features on the MVP, modify the Active Architecture. For major protocol upgrades, begin the work of integrating the Planned Architecture into the Active Architecture.

## 3. Critical Design Decisions & Fixes (DO NOT REVERT)

Over 17 iterations, several critical decisions were made to solve specific hardware/BLE issues. **These must be preserved.**

### 3.1 Device Detection: RSSI Staleness (Not Blacklists)
- **Problem:** BLE devices that are turned off often remain in the Android BLE cache, appearing as "ghost" devices that cannot be connected to.
- **Failed Approach:** Using a timeout blacklist. It was too complex and buggy.
- **Current Solution:** Hybrid RSSI Staleness.
  - Real devices have fluctuating RSSI values.
  - Cached/ghost devices have static RSSI values.
  - The `LegoHub` class tracks `rssiStaleCount`. If the RSSI hasn't changed for 3 consecutive scans AND the timestamp is old, the device is hidden from the UI.
  - **Code Location:** `LegoSpikePrime.java` -> `LegoHub.update()` and `CheckAllDevices()`.

### 3.2 Null Pointer Protection
- **Problem:** Asynchronous BLE events often trigger when device addresses or names are null, causing hard crashes.
- **Current Solution:** Strict null checking before any HashMap access or list iteration.
- **Code Location:** `LegoSpikePrime.java` -> `HubListChanged` event logic (lines 646-684). It uses a safe nested loop approach rather than relying on HashMaps with potentially null keys.

### 3.3 UUID Authentication
- **Problem:** Connecting to the wrong BLE device.
- **Current Solution:** Strict UUID verification using the official SPIKE Prime UUIDs, NOT the general LEGO Wireless Protocol UUIDs.
- **Correct UUIDs:**
  - Service: `0000fd02-0000-1000-8000-00805f9b34fb`
  - RX: `0000fd02-0001-1000-8000-00805f9b34fb`
  - TX: `0000fd02-0002-1000-8000-00805f9b34fb`
- **Code Location:** `BluetoothInterfaceImpl.java`.

### 3.4 Scanning State Management
- **Problem:** Scanning while trying to connect causes connection failures.
- **Current Solution:** The extension explicitly stops scanning before initiating a connection, and uses a `wasScanningBeforeConnection` flag to resume scanning if the connection fails or drops.

## 4. Future Extensibility Strategy
To support older/legacy LEGO robotics products in the future:
1. The `BluetoothInterfaceImpl` should be abstracted into an interface (`ILegoBluetooth`).
2. Create specific implementations (e.g., `SpikePrimeBluetoothImpl`, `Ev3BluetoothImpl`).
3. The main `LegoSpikePrime` class should act as a facade, routing commands to the appropriate implementation based on the connected device's advertised services.
4. The Planned Architecture (`io.github.appinventor.legospike`) provides the foundation for this modular approach.
