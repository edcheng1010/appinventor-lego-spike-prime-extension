> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../../NOTICE) for trademark and licensing details.*

# LEGO Official app.py — Line-by-Line Flow Analysis

## UUIDs (AUTHORITATIVE — confirmed in source code)
```
SERVICE = "0000fd02-0000-1000-8000-00805f9b34fb"
RX_CHAR = "0000fd02-0001-1000-8000-00805f9b34fb"  # Hub RECEIVES data on this
TX_CHAR = "0000fd02-0002-1000-8000-00805f9b34fb"  # Hub TRANSMITS data on this
```

## Connection Flow (EXACT SEQUENCE)
1. **Scan** for devices advertising SERVICE UUID
2. **Connect** using BleakClient
3. **Get service/characteristics** from client.services
4. **Start notifications** on TX_CHAR (subscribe to hub's output)
5. **Send InfoRequest** (0x00) → wait for InfoResponse (0x01)
   - This gives us: max_packet_size, max_message_size, max_chunk_size
6. **Send DeviceNotificationRequest** (0x28) with interval_ms → wait for response (0x29)
7. **Send ClearSlotRequest** (0x46) for target slot → wait for response (0x47)
   - Note: failure here is OK (slot may already be empty)
8. **Compute CRC32** of entire program with 4-byte alignment padding
9. **Send StartFileUploadRequest** (0x0C) with filename, slot, crc → wait for response (0x0D)
10. **Transfer chunks** in a loop:
    - Chunk size = info_response.max_chunk_size
    - For each chunk: compute running_crc = crc(chunk, previous_running_crc)
    - Send TransferChunkRequest (0x10) with running_crc and chunk data
    - Wait for TransferChunkResponse (0x11) — must be success
11. **Send ProgramFlowRequest** (0x1E) with stop=False, slot → wait for response (0x1F)

## CRITICAL IMPLEMENTATION DETAILS

### Packet Fragmentation
```python
packet_size = info_response.max_packet_size if info_response else len(frame)
for i in range(0, len(frame), packet_size):
    packet = frame[i : i + packet_size]
    await client.write_gatt_char(rx_char, packet, response=False)
```
- COBS-packed frames may exceed BLE MTU
- Must split into packets of max_packet_size
- Each packet is written with `response=False` (write without response)

### Response Tracking
- Simple future-based: store expected response ID, resolve when matching response arrives
- Only ONE pending request at a time (sequential, not pipelined)

### Incomplete Message Handling
```python
if data[-1] != 0x02:
    # packet is not a complete message — NOT HANDLED in this example
```
- The official example does NOT handle fragmented incoming messages
- In production, we MUST buffer incoming data until we see delimiter 0x02

### Data Reception Flow
1. Receive raw bytes from TX_CHAR notification
2. Check if last byte is 0x02 (delimiter) — if not, it's incomplete
3. Call cobs.unpack(data) to decode
4. Call deserialize(data) to parse into message object
5. If message ID matches pending response, resolve the future

### Example Program
```python
EXAMPLE_PROGRAM = """import runloop
from hub import light_matrix
print("Console message from hub.")
async def main():
    await light_matrix.write("Hello, world!")
runloop.run(main())""".encode("utf8")
```
- Programs are UTF-8 encoded Python source code
- They use the SPIKE 3.x MicroPython API (runloop, hub.light_matrix)
- The program is uploaded as raw bytes, NOT as a file path

### CRC Computation for Upload
1. Full program CRC: `crc(EXAMPLE_PROGRAM)` — with 4-byte alignment padding
2. Running CRC per chunk: `running_crc = crc(chunk, previous_running_crc)` — cumulative
3. Both use the same crc function from crc.py

## WHAT THIS EXAMPLE DOES NOT COVER
- TunnelMessage (0x32) — NOT in the official example
- Motor control — NOT in the official example
- Sensor reading via tunnel — NOT in the official example
- Message buffering for fragmented BLE packets — NOT implemented
- Reconnection logic — NOT implemented
