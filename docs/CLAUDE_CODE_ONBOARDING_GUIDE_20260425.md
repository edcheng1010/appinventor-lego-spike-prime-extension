# Claude Code Onboarding & Execution Guide

**Date:** April 25, 2026
**Project:** LEGO SPIKE Prime App Inventor Extension
**Author:** Manus AI (Project Manager)

This guide provides the exact, step-by-step instructions for Edward to execute the implementation plan using Claude Code. It includes the precise prompts to use for each phase to ensure Claude Code stays on track and doesn't hallucinate.

---

## Part 1: Initial Setup & Launch

1. **Open Command Prompt** on your Windows machine.
2. **Navigate to the project directory:**
   ```cmd
   cd C:\Projects\appinventor-lego-spike-prime-extension
   ```
3. **Pull the latest changes** (this includes all the deep analysis we just did):
   ```cmd
   git pull origin main
   ```
4. **Launch Claude Code:**
   ```cmd
   claude
   ```
   *(Claude Code will automatically read the `CLAUDE.md` file to understand the project context).*

---

## Part 2: Execution Prompts (Phase by Phase)

**CRITICAL RULE:** Do not copy-paste all prompts at once. Execute one task, review the code changes, approve them, compile, and test. Only move to the next task when the current one is verified.

### Phase 1: Core Protocol Infrastructure

**Prompt for Task 1.1 (COBS Encoding):**
> "Read `docs/IMPLEMENTATION_PLAN.md`, `docs/BYTE_LEVEL_PROTOCOL.md`, and `docs/deep_analysis/04_cobs_test_vectors.md`. I need you to implement the custom COBS encoding algorithm in `io.github.appinventor.legospike.COBSEncoder.java`. It must escape 0x00, 0x01, and 0x02. Use the exact constants specified in the byte-level protocol doc. After writing the class, write a JUnit test class that verifies your implementation against the three test vectors provided in `04_cobs_test_vectors.md`. Do not proceed to any other task until these tests pass."

**Prompt for Task 1.2 (CRC32):**
> "Now implement the CRC32 checksum logic in `io.github.appinventor.legospike.SpikeCRC32.java`. According to the protocol docs, the data MUST be padded to 4-byte alignment with 0x00 bytes before calculating the CRC. It must also support a running CRC (using the previous CRC as a seed). Write a unit test to verify this behavior."

**Prompt for Task 1.3 (Message Framing):**
> "Implement the message framing and XOR obfuscation in `io.github.appinventor.legospike.MessageFramer.java`. It needs a `pack` method that takes raw bytes, applies COBS encoding, XORs every byte with 0x03, and then adds the 0x01 prefix and 0x02 suffix. It also needs an `unpack` method that does the reverse. Write a unit test to verify that `unpack(pack(data))` equals the original data."

*Stop here. Compile the project (`ant extensions`) to ensure there are no syntax errors. Commit your changes to Git.*

---

### Phase 2: BLE Connection & Initialization

**Prompt for Task 2.1 & 2.2 (Connection & Handshake):**
> "Read `docs/BYTE_LEVEL_PROTOCOL.md` Section 1. We need to update `io.github.appinventor.legospikeprime.BluetoothInterfaceImpl.java`. 
> 1. Ensure we are scanning for the correct Service UUID: `00001623-1212-efde-1623-785feabcd123`.
> 2. Ensure we subscribe to the TX Characteristic: `00001625-1212-efde-1623-785feabcd123`.
> 3. Implement the mandatory `InfoRequest` (0x00) handshake. Immediately after subscribing to the TX characteristic, we must construct an InfoRequest message, pack it using our new `MessageFramer`, and write it to the RX Characteristic (`00001624-1212-efde-1623-785feabcd123`)."

*Stop here. Compile the project. Install the `.aix` on your Android device. Connect to a physical SPIKE Prime hub. Use `adb logcat` to verify that the InfoRequest is sent and an InfoResponse is received.*

---

### Phase 3: The Two-Part Architecture (Program Upload)

**Prompt for Task 3.1 & 3.2 (Hub Script & Upload Flow):**
> "Read `docs/BYTE_LEVEL_PROTOCOL.md` Sections 2 and 4. We need to implement the program upload flow.
> 1. Create a constant string in our Java code containing the Python hub script shown in Section 2.
> 2. Implement the sequence of messages to upload this script: `ClearSlotRequest` (0x46), `StartFileUploadRequest` (0x0C), `TransferChunkRequest` (0x10), and `ProgramFlowRequest` (0x1E).
> 3. Add a method to trigger this upload sequence automatically after the `InfoRequest` handshake completes successfully."

*Stop here. Compile and test on the physical device. When you connect, you should see the SPIKE Prime hub's screen change or indicate that a program has started running.*

---

### Phase 4: App Inventor Extension API

**Prompt for Task 4.1 (Motor Control):**
> "Now that the Python script is running on the hub and listening to the `module_tunnel`, we need to expose motor control to App Inventor. In `io.github.appinventor.legospikeprime.LegoSpikePrime.java`, add a `@SimpleFunction` called `MotorRun`. It should take a port letter (A-F) and a speed (-100 to 100). It must construct a custom string payload (e.g., 'A100'), wrap it in a `TunnelMessage` (0x32), pack it using `MessageFramer`, and send it via BLE. Ensure the payload format exactly matches what our Python hub script expects."

*Stop here. Compile. Create a simple App Inventor app with a button that calls `MotorRun`. Test it with a real motor connected to the hub.*

---

## Part 3: Troubleshooting with Claude Code

If you hit an error during compilation or testing, do **NOT** just say "it doesn't work." Give Claude Code the exact error message or log output.

**Example Troubleshooting Prompt:**
> "When I call `MotorRun`, the app crashes. Here is the `adb logcat` output: [paste logcat here]. Review the `MessageFramer.java` code and the logcat to identify why the byte array is causing a NullPointerException."

If Claude Code gets confused about the protocol, remind it:
> "Stop. You are hallucinating the protocol. Read `docs/BYTE_LEVEL_PROTOCOL.md` again. We cannot send direct motor commands. We MUST use TunnelMessage (0x32)."
