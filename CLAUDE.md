# Claude Code Project Memory

## Project Context
This is an MIT App Inventor extension for LEGO SPIKE Prime hubs, developed for the MIT Hong Kong Innovation Node. The goal is to provide a highly reliable, crash-free Bluetooth Low Energy (BLE) connection experience for classrooms with multiple hubs.

## Critical Architectural Rules (DO NOT VIOLATE)

1. **NEVER overwrite the SPIKE Prime UUIDs.**
   - Service: `0000fd02-0000-1000-8000-00805f9b34fb`
   - RX: `0000fd02-0001-1000-8000-00805f9b34fb`
   - TX: `0000fd02-0002-1000-8000-00805f9b34fb`
   - *Note: These are specific to SPIKE Prime. Do not use the general LEGO Wireless Protocol UUIDs (`00001623...`).*

2. **NEVER remove the RSSI Staleness Logic.**
   - Device detection relies on RSSI staleness, NOT timeouts or blacklists.
   - If a device's RSSI doesn't change for 3 consecutive scans, it is considered a "ghost" device (turned off but cached by Android) and must be hidden.
   - This logic is in `LegoSpikePrime.java` -> `LegoHub.update()`.

3. **ALWAYS check for nulls in asynchronous BLE events.**
   - Android BLE callbacks often fire with null device names or addresses.
   - Before accessing any HashMap or list with a device address, verify `if (address != null)`.
   - The `HubListChanged` event logic specifically uses a safe nested loop approach to avoid `NullPointerException`s. Do not refactor this into a HashMap-based approach without strict null safety.

4. **ALWAYS manage scanning state before connecting.**
   - You must stop scanning before initiating a connection to a hub.
   - Use the `wasScanningBeforeConnection` flag to resume scanning if the connection fails or drops.

## Codebase Structure (The "Split")

There are two packages in this repository:

1. **`io.github.appinventor.legospikeprime` (The Active MVP)**
   - Contains `LegoSpikePrime.java` and `BluetoothInterfaceImpl.java`.
   - This is the working, self-contained extension. **Modify these files for immediate bug fixes or minor features.**

2. **`io.github.appinventor.legospike` (The Planned Architecture)**
   - Contains 13 helper classes (`SpikeProtocol.java`, `COBSEncoder.java`, etc.).
   - These are currently **NOT INTEGRATED** into the main extension.
   - They represent a more robust protocol implementation for future extensibility to other LEGO hubs.

## Development Workflow

1. **Read `ARCHITECTURE.md`** before proposing any major refactoring.
2. **Compile frequently** using the provided Ant build script (`build.xml` or `compile_windows.bat`).
3. **Test on physical devices.** The App Inventor emulator cannot test BLE extensions reliably.
4. **Commit small, logical changes.** Use Git branching for experimental features.

## Known Issues / Next Steps
- The immediate next step is to implement UUID-based device authentication *after* connecting, to ensure the connected device is actually a SPIKE Prime hub before sending commands.
- Long-term goal: Integrate the 13 helper classes from the `legospike` package into the main `legospikeprime` extension to support full COBS encoding and CRC32 verification.
