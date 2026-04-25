# SPIKE Prime 3.x BLE Protocol: Exact Byte-Level Flow

**Last Updated:** April 26, 2026
**Verified Against:** LEGO official `app.py` + etomasfe/SpikeRemoteControl (the only two proven working implementations)

---

## 1. BLE UUIDs (VERIFIED)

There are two UUID sets in the LEGO ecosystem. We use the **SPIKE Prime 3.x** set:

| Purpose | UUID | Source |
|---------|------|--------|
| **Service** | `0000fd02-0000-1000-8000-00805f9b34fb` | etomasfe + CLAUDE.md |
| **RX (write to hub)** | `0000fd02-0001-1000-8000-00805f9b34fb` | etomasfe + CLAUDE.md |
| **TX (read from hub)** | `0000fd02-0002-1000-8000-00805f9b34fb` | etomasfe + CLAUDE.md |

> **WARNING:** Do NOT use `00001623-1212-efde-1623-785feabcd123`. That is the generic LEGO Wireless Protocol UUID for SPIKE Essential, Boost, and Technic hubs. It will NOT work with SPIKE Prime 3.x firmware.

---

## 2. Connection & Initialization

1. **Scan for BLE devices** advertising Service UUID `0000fd02-0000-1000-8000-00805f9b34fb`
2. **Connect to GATT server**
3. **Discover services** and get the primary service (`0000fd02-0000-...`)
4. **Get RX characteristic** (`0000fd02-0001-...`) for writing TO the hub
5. **Get TX characteristic** (`0000fd02-0002-...`) for reading FROM the hub
6. **Enable notifications** on TX characteristic
7. **Send InfoRequest** (Message ID `0x00`, no payload) — this is the mandatory handshake that establishes max packet sizes

> **Note:** The etomasfe implementation skips the InfoRequest and hardcodes chunk sizes to 445 bytes. This works but is not robust. The LEGO official `app.py` sends InfoRequest first.

---

## 3. The Two-Part Architecture (The "Secret")

You CANNOT send direct motor commands (like `motor.run()`) over BLE to SPIKE 3.x. The firmware does not support it. You MUST use the **TunnelMessage** architecture:

**Part A: Upload a Python script to the hub** that listens on the `module_tunnel`:

```python
from hub import light_matrix
import motor
from hub import port
light_matrix.set_pixel(1,1,100)
import hub
tunnel = hub.config['module_tunnel']
def receive_tunnel_message(data):
    if data==b"bye.bye.AB":
        quit()    
    if True:
        n=''
        for i in range(1,5):
            n=n+chr(data[i])
        mA=int(n)*10
        n=''
        for i in range(6,10):
            n=n+chr(data[i])
        mB=int(n)*10
        if chr(data[0])=='A':
            motor.run(port.A,mA)
        elif chr(data[0])=='B':
            motor.run(port.B,mA)
        elif chr(data[0])=='C':
            motor.run(port.C,mA)
        elif chr(data[0])=='D':
            motor.run(port.D,mA)
        elif chr(data[0])=='E':
            motor.run(port.E,mA)
        elif chr(data[0])=='F':
            motor.run(port.F,mA)
        if chr(data[5])=='A':
            motor.run(port.A,mB)
        elif chr(data[5])=='B':
            motor.run(port.B,mB)
        elif chr(data[5])=='C':
            motor.run(port.C,mB)
        elif chr(data[5])=='D':
            motor.run(port.D,mB)
        elif chr(data[5])=='E':
            motor.run(port.E,mB)
        elif chr(data[5])=='F':
            motor.run(port.F,mB)
    tunnel.send(b'rdy')
tunnel.callback(receive_tunnel_message)
tunnel.send(b'rdy')
while True:   
    pass
```

**Part B: Send TunnelMessage (0x32) packets** from the App Inventor extension containing motor commands.

---

## 4. COBS Encoding Constants (VERIFIED)

| Constant | Value | Description |
|----------|-------|-------------|
| `DELIMITER` | `0x02` | End-of-message marker |
| `NO_DELIMITER` | `255` (`0xFF`) | Indicates no delimiter in block |
| `MAX_BLOCK_SIZE` | `84` | Maximum bytes per COBS block |
| `COBS_CODE_OFFSET` | `2` | Added to block size in code word |
| `XOR` | `0x03` | XOR obfuscation key |

---

## 5. Message Encoding Pipeline

Every message sent to the RX Characteristic MUST go through this exact pipeline:

**Step 5.1: Construct the raw message**

Format: `[Message ID (uint8)] [Payload Bytes...]`

Example — TunnelMessage sending "A+050B+050":
- Message ID: `0x32` (TunnelMessage)
- Payload Size: `0x0a, 0x00` (10 bytes, little-endian uint16)
- Payload Data: `A+050B+050` (UTF-8 encoded)
- Raw Bytes: `[0x32, 0x0a, 0x00, 0x41, 0x2b, 0x30, 0x35, 0x30, 0x42, 0x2b, 0x30, 0x35, 0x30]`

**Step 5.2: COBS Encode**

The custom COBS algorithm escapes bytes `0x00`, `0x01`, and `0x02` (any byte <= DELIMITER).

**Step 5.3: XOR every byte with 0x03**

`encoded_byte = cobs_byte ^ 0x03`

**Step 5.4: Frame the message**

- Prefix with `0x01` (frame start)
- Suffix with `0x02` (frame end / delimiter)

---

## 6. Program Upload Flow

To upload the Python script to the hub, execute this exact sequence:

**Step 6.1: ClearSlotRequest**
```
Message ID: 0x46
Payload: [slot_id=0x00]
Raw: [0x46, 0x00]
```

**Step 6.2: StartFileUploadRequest**
```
Message ID: 0x0C
Payload: [name="program.py\0" (32 bytes, null-padded), slot_id=0x00, crc32 (4 bytes, little-endian)]
```
The CRC32 is calculated over the entire Python program string.

**Step 6.3: TransferChunkRequest (repeat until all data sent)**
```
Message ID: 0x10
Payload: [running_crc32 (4 bytes LE), chunk_size (2 bytes LE), chunk_data (bytes)]
```
- Chunk size: 445 bytes (hardcoded in etomasfe; dynamic if using InfoResponse)
- Running CRC: First chunk uses seed 0; subsequent chunks use previous CRC as seed
- CRC bytes are little-endian (reversed)

**Step 6.4: ProgramFlowRequest (Start)**
```
Message ID: 0x1E
Payload: [action=0x00 (stop first), slot_id=0x00]
Raw: [0x1E, 0x00, 0x00]
```

---

## 7. TunnelMessage Command Format

The etomasfe implementation uses a fixed 10-character command format:

```
{port1}{sign}{3digits}{port2}{sign}{3digits}
```

Examples:
- `A+050B+050` = Motor A forward at 500 deg/s, Motor B forward at 500 deg/s
- `A-050B+000` = Motor A reverse at 500 deg/s, Motor B stopped
- `bye.bye.AB` = Quit the program on the hub

The hub-side Python script multiplies the 3-digit value by 10 to get the actual speed.

---

## 8. The "rdy" Handshake

After processing each command, the hub Python script sends `tunnel.send(b'rdy')` back via TunnelMessage. The app must wait for this acknowledgment before sending the next command. This prevents command flooding.

---

## 9. Receiving Data (Decoding Pipeline)

When the hub sends data back via the TX Characteristic:

1. **De-frame:** Remove leading `0x01` and trailing `0x02`
2. **Un-XOR:** XOR every byte with `0x03`
3. **COBS Decode:** Reverse the custom COBS algorithm
4. **Parse Message:** Check Message ID
   - `0x3C` (DeviceNotification): Battery, IMU, Motor position, etc.
   - `0x32` (TunnelMessage): Custom data from your Python script (e.g., "rdy")
