> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../../NOTICE) for trademark and licensing details.*

# LEGO Official messages.py — Line-by-Line Analysis

## Message ID Map (AUTHORITATIVE)
| ID (hex) | Name | Direction | Struct Format |
|----------|------|-----------|---------------|
| 0x00 | InfoRequest | App→Hub | `b"\0"` (just the ID byte) |
| 0x01 | InfoResponse | Hub→App | `<BBBHBBHHHHH` |
| 0x0C | StartFileUploadRequest | App→Hub | `<B{name_len+1}sBI` |
| 0x0D | StartFileUploadResponse | Hub→App | `<BB` (status) |
| 0x10 | TransferChunkRequest | App→Hub | `<BIH{size}s` |
| 0x11 | TransferChunkResponse | Hub→App | `<BB` (status) |
| 0x1E | ProgramFlowRequest | App→Hub | `<BBB` |
| 0x1F | ProgramFlowResponse | Hub→App | `<BB` (status) |
| 0x20 | ProgramFlowNotification | Hub→App | `<BB` |
| 0x21 | ConsoleNotification | Hub→App | text after ID byte |
| 0x28 | DeviceNotificationRequest | App→Hub | `<BH` |
| 0x29 | DeviceNotificationResponse | Hub→App | `<BB` (status) |
| 0x3C | DeviceNotification | Hub→App | `<BH` + payload |
| 0x46 | ClearSlotRequest | App→Hub | `<BB` |
| 0x47 | ClearSlotResponse | Hub→App | `<BB` (status) |

## CRITICAL: TunnelMessage is NOT in the official messages.py!
The official LEGO reference does NOT include TunnelMessage (0x32). This is documented
in the protocol docs but not implemented in the example code. We need to check
etomasfe/SpikeRemoteControl for how TunnelMessage is actually structured.

## Serialization Details

### InfoRequest (0x00)
- Serialize: just `b"\0"` — a single zero byte
- NOTE: This is the ID byte itself (0x00)

### InfoResponse (0x01)
- Deserialize: `<BBBHBBHHHHH`
- Fields: id, rpc_major, rpc_minor, rpc_build(uint16), firmware_major, firmware_minor, firmware_build(uint16), max_packet_size(uint16), max_message_size(uint16), max_chunk_size(uint16), product_group_device(uint16)
- CRITICAL: max_chunk_size tells us the maximum chunk size for file upload

### ClearSlotRequest (0x46)
- Serialize: `<BB` = [0x46, slot]

### StartFileUploadRequest (0x0C)
- Serialize: `<B{name_len+1}sBI`
- Fields: [0x0C, file_name_null_terminated, slot, crc32]
- File name is UTF-8 encoded, null-terminated, max 31 bytes + null = 32
- CRC32 is the CRC of the ENTIRE file content (with 4-byte alignment padding)

### TransferChunkRequest (0x10)
- Serialize: `<BIH{size}s`
- Fields: [0x10, running_crc32(uint32), chunk_size(uint16), chunk_data]
- running_crc is the CRC32 of all data sent SO FAR (cumulative)

### ProgramFlowRequest (0x1E)
- Serialize: `<BBB` = [0x1E, stop_flag, slot]
- stop=0 means START, stop=1 means STOP

### DeviceNotificationRequest (0x28)
- Serialize: `<BH` = [0x28, interval_ms(uint16)]
- Requests periodic sensor data at the given interval

### DeviceNotification (0x3C)
- Contains sub-messages for different device types:
  - 0x00: Battery `<BB`
  - 0x01: IMU `<BBBhhhhhhhhh`
  - 0x0A: Motor `<BBBhhbi`
  - 0x0B: Force `<BBBB`
  - 0x0C: Color `<BBbHHH`
  - 0x0D: Distance `<BBh`
  - 0x0E: 3x3 `<BB9B`
  - 0x02: 5x5 `<B25B`

## StatusResponse Pattern
All status responses use the same pattern: `<BB` where second byte 0x00 = success.

## CRC32 Details (from crc.py)
- Uses standard Python `binascii.crc32`
- Pads data to 4-byte alignment with null bytes before computing
- Takes optional seed parameter (default 0)
- Running CRC: each chunk's CRC uses the previous CRC as seed
