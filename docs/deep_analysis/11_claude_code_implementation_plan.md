> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../../NOTICE) for trademark and licensing details.*

# Strategic Implementation Plan for Claude Code

Based on the exhaustive analysis of all 41 documentation sources, here is the step-by-step, highly strategic implementation plan for Claude Code. This plan is designed to minimize token usage, prevent hallucinations, and ensure each step is testable before moving to the next.

## Phase 1: Core Protocol Infrastructure (The Foundation)
**Goal:** Implement the byte-level encoding/decoding pipeline perfectly.

### Task 1.1: Implement COBS Encoding/Decoding
- **Action:** Write `COBSEncoder.java`
- **Requirements:** Must exactly match the LEGO custom COBS algorithm (escapes 0x00, 0x01, 0x02).
- **Test:** Write unit tests using the exact test vectors from the official `cobs.py` tests.

### Task 1.2: Implement CRC32 Checksum
- **Action:** Write `SpikeCRC32.java`
- **Requirements:** Must pad data to 4-byte alignment with 0x00 before calculating. Must support running CRC.
- **Test:** Verify against known CRC32 values from the official documentation.

### Task 1.3: Implement Message Framing & XOR Obfuscation
- **Action:** Write `MessageFramer.java`
- **Requirements:** Must XOR all bytes with 0x03. Must prefix with 0x01 and suffix with 0x02.
- **Test:** Verify the full pipeline (Raw -> COBS -> XOR -> Frame) against known good packets.

## Phase 2: BLE Connection & Initialization
**Goal:** Establish a stable BLE connection and complete the mandatory handshake.

### Task 2.1: BLE Scanning & Connection
- **Action:** Update `BluetoothInterfaceImpl.java`
- **Requirements:** Scan for Service UUID `00001623-1212-efde-1623-785feabcd123`. Connect and subscribe to TX Characteristic `00001625-1212-efde-1623-785feabcd123`.

### Task 2.2: The Mandatory InfoRequest Handshake
- **Action:** Implement the `InfoRequest` (0x00) message.
- **Requirements:** Send this message immediately upon connection. Parse the `InfoResponse` (0x01) to get max packet sizes.
- **Test:** Connect to a real hub and verify the `InfoResponse` is received and parsed correctly.

## Phase 3: The Two-Part Architecture (Program Upload)
**Goal:** Successfully upload and start the Python controller script on the hub.

### Task 3.1: Implement Program Upload Flow
- **Action:** Implement `ClearSlotRequest`, `StartFileUploadRequest`, `TransferChunkRequest`, and `ProgramFlowRequest`.
- **Requirements:** Handle chunking based on the max packet size from the `InfoResponse`. Handle the running CRC32.

### Task 3.2: Write the Hub Python Script
- **Action:** Write `controller.py` (to be embedded in the Java code as a string).
- **Requirements:** Must use `hub.config['module_tunnel']`. Must parse incoming custom commands and execute motor/sensor actions.

### Task 3.3: Automate the Upload
- **Action:** Create a method in the extension that automatically uploads and starts `controller.py` upon successful connection.
- **Test:** Connect to the hub, trigger the upload, and verify the hub screen indicates the program is running.

## Phase 4: App Inventor Extension API (The User Interface)
**Goal:** Expose the functionality to App Inventor users via blocks.

### Task 4.1: Implement Motor Control Blocks
- **Action:** Add `@SimpleFunction` methods for `MotorRun`, `MotorStop`, etc.
- **Requirements:** These methods must construct a custom payload (e.g., "A100"), wrap it in a `TunnelMessage` (0x32), encode it, and send it over BLE.

### Task 4.2: Implement Sensor Reading Blocks
- **Action:** Add `@SimpleEvent` methods for sensor updates.
- **Requirements:** The Python script on the hub must periodically send sensor data back via `TunnelMessage`. The Java code must decode, parse, and trigger the App Inventor events.

## Phase 5: Final Integration & Testing
**Goal:** Ensure everything works together seamlessly.

### Task 5.1: End-to-End Testing
- **Action:** Build the `.aix` file. Create a test App Inventor project.
- **Requirements:** Test connection, motor control, and sensor reading on a real Android device with a real SPIKE Prime hub.

### Task 5.2: Documentation & Cleanup
- **Action:** Finalize all JavaDoc comments. Ensure all `@DesignerProperty` annotations are correct.

---
**Instructions for Claude Code:**
When starting a task, read this document and the `PROJECT_BRAIN.md` file first. Do not proceed to the next task until the current one is fully implemented and verified.
