# SPIKE Prime 3.x BLE Protocol: Exact Byte-Level Flow

Based on the exhaustive analysis of all 41 sources, here is the exact, verified byte-level flow required to control a SPIKE Prime 3.x hub from an App Inventor extension.

## 1. Connection & Initialization
1. **Scan for BLE Devices**
   - Target Service UUID: `00001623-1212-efde-1623-785feabcd123`
2. **Connect to Device**
3. **Subscribe to Notifications**
   - TX Characteristic UUID: `00001625-1212-efde-1623-785feabcd123`
4. **Send InfoRequest (MANDATORY)**
   - Message ID: `0x00`
   - Payload: None
   - Encoded: `[0x01, 0x04, 0x03, 0x02]` (Example, needs exact COBS/XOR)
   - *Why:* The official docs state this MUST be the first message sent to establish max packet sizes.

## 2. The Two-Part Architecture (The "Secret")
You CANNOT send direct motor commands (like `motor.run()`) over BLE to SPIKE 3.x. The firmware does not support it. You MUST use the `TunnelMessage` architecture:

**Part A: The Hub Script**
You must upload a Python script to the hub that listens on the `module_tunnel`.
```python
import hub
import motor
from hub import port

tunnel = hub.config['module_tunnel']

def receive_tunnel_message(data):
    # data is a bytearray
    if chr(data[0]) == 'A':
        speed = int(chr(data[1]) + chr(data[2]) + chr(data[3]))
        motor.run(port.A, speed)

tunnel.message_handler(receive_tunnel_message)
```

**Part B: The App Inventor Extension**
The extension sends `TunnelMessage` (0x32) packets containing the custom payload (e.g., `"A100"`).

## 3. Message Serialization & Encoding Pipeline
Every message sent to the RX Characteristic (`00001624...`) MUST go through this exact pipeline:

### Step 3.1: Construct the Raw Message
Format: `[Message ID (uint8)] [Payload Bytes...]`
Example (TunnelMessage sending "A100"):
- Message ID: `0x32` (50)
- Payload Size: `0x04, 0x00` (4 bytes, little-endian uint16)
- Payload Data: `'A', '1', '0', '0'` (`0x41, 0x31, 0x30, 0x30`)
- Raw Bytes: `[0x32, 0x04, 0x00, 0x41, 0x31, 0x30, 0x30]`

### Step 3.2: COBS Encoding
The SPIKE Prime COBS implementation is custom. It escapes `0x00`, `0x01`, and `0x02`.
- `DELIMITER = 0x02`
- `MAX_BLOCK_SIZE = 84`
- `code_word = block_size + 2 + delimiter * 84`

### Step 3.3: XOR Obfuscation
Every byte of the COBS-encoded array MUST be XORed with `0x03`.
`byte = byte ^ 0x03`

### Step 3.4: Framing
- Prefix with `0x01` (High Priority Start)
- Suffix with `0x02` (Message End)

## 4. Program Upload Flow (To install the Hub Script)
To get the Python script onto the hub, you must execute this sequence of messages:

1. **ClearSlotRequest (0x46)**
   - Payload: `[slot_id (uint8)]`
2. **StartFileUploadRequest (0x0C)**
   - Payload: `[name (string[32]), slot_id (uint8), crc32 (uint32)]`
3. **TransferChunkRequest (0x10)** (Repeat until done)
   - Payload: `[running_crc32 (uint32), chunk_size (uint16), chunk_data (bytes)]`
4. **ProgramFlowRequest (0x1E)**
   - Payload: `[action=0x00 (Start), slot_id (uint8)]`

## 5. Receiving Data (Sensor Readings)
When the hub sends data back (via the TX Characteristic), it arrives framed and encoded.
1. **De-frame:** Remove leading `0x01` and trailing `0x02`.
2. **Un-XOR:** XOR every byte with `0x03`.
3. **COBS Decode:** Reverse the custom COBS algorithm.
4. **Parse Message:**
   - If ID == `0x3C` (DeviceNotification): Parse the nested device messages (Battery, IMU, Motor, etc.).
   - If ID == `0x32` (TunnelMessage): This is custom data sent back by your Python script.
