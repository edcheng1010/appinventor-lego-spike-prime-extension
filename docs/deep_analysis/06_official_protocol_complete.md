# Official SPIKE Prime Protocol — Complete Analysis

## Source: LEGO/spike-prime-docs (all .rst files)

## COMPLETE MESSAGE TYPE TABLE (AUTHORITATIVE)

| ID (dec) | ID (hex) | Name | Direction |
|----------|----------|------|-----------|
| 0 | 0x00 | InfoRequest | App→Hub |
| 1 | 0x01 | InfoResponse | Hub→App |
| 10 | 0x0A | StartFirmwareUploadRequest | App→Hub |
| 11 | 0x0B | StartFirmwareUploadResponse | Hub→App |
| 12 | 0x0C | StartFileUploadRequest | App→Hub |
| 13 | 0x0D | StartFileUploadResponse | Hub→App |
| 16 | 0x10 | TransferChunkRequest | App→Hub |
| 17 | 0x11 | TransferChunkResponse | Hub→App |
| 20 | 0x14 | BeginFirmwareUpdateRequest | App→Hub |
| 21 | 0x15 | BeginFirmwareUpdateResponse | Hub→App |
| 22 | 0x16 | SetHubNameRequest | App→Hub |
| 23 | 0x17 | SetHubNameResponse | Hub→App |
| 24 | 0x18 | GetHubNameRequest | App→Hub |
| 25 | 0x19 | GetHubNameResponse | Hub→App |
| 26 | 0x1A | DeviceUuidRequest | App→Hub |
| 27 | 0x1B | DeviceUuidResponse | Hub→App |
| 30 | 0x1E | ProgramFlowRequest | App→Hub |
| 31 | 0x1F | ProgramFlowResponse | Hub→App |
| 32 | 0x20 | ProgramFlowNotification | Hub→App |
| 33 | 0x21 | ConsoleNotification | Hub→App |
| 40 | 0x28 | DeviceNotificationRequest | App→Hub |
| 41 | 0x29 | DeviceNotificationResponse | Hub→App |
| 50 | 0x32 | TunnelMessage | Bidirectional |
| 60 | 0x3C | DeviceNotification | Hub→App |
| 70 | 0x46 | ClearSlotRequest | App→Hub |
| 71 | 0x47 | ClearSlotResponse | Hub→App |

## TunnelMessage (0x32) — THE KEY MESSAGE
- ID: 50 (0x32)
- Direction: BIDIRECTIONAL (both App→Hub and Hub→App)
- Structure: [0x32] [size_low:uint8] [size_high:uint8] [payload:uint8[size]]
- size is uint16 little-endian
- This is how we send commands to the running Python program on the hub
- This is how the hub sends data back to us

## ENCODING RULES (AUTHORITATIVE)
1. COBS encoding: escapes bytes 0x00, 0x01, 0x02
2. XOR all bytes with 0x03
3. Append delimiter 0x02

### COBS Code Word Formula:
code_word = block_size + 2 + delimiter * 84

### Code Word Ranges:
| Range | Block Size | Delimiter |
|-------|-----------|-----------|
| 0-2 | N/A | N/A |
| 3-86 | n-3 | 0x00 |
| 87-170 | n-87 | 0x01 |
| 171-254 | n-171 | 0x02 |
| 255 | 84 | N/A (no delimiter) |

### Constants:
- DELIMITER = 0x02
- NO_DELIMITER = 0xFF (255)
- MAX_BLOCK_SIZE = 84
- COBS_CODE_OFFSET = 2
- XOR_VALUE = 0x03

## PRIORITY SYSTEM
- 0x01 prefix = high-priority message start
- 0x02 suffix = message end (always required)
- Low-priority messages have no prefix, just 0x02 at end
- High-priority can interrupt low-priority

## CRC32 RULES
- Standard CRC32 algorithm
- Data MUST be padded to 4-byte alignment with 0x00 bytes before computing
- Running CRC: use previous CRC as seed for next chunk

## ENUMERATION VALUES

### Hub Ports:
| Value | Port |
|-------|------|
| 0x00 | A |
| 0x01 | B |
| 0x02 | C |
| 0x03 | D |
| 0x04 | E |
| 0x05 | F |

### Motor Device Types:
| Value | Type |
|-------|------|
| 0x30 | Medium Motor |
| 0x31 | Large Motor |
| 0x41 | Small Motor |

### Colors:
| Value | Color |
|-------|-------|
| 0x00 | Black |
| 0x01 | Magenta |
| 0x02 | Purple |
| 0x03 | Blue |
| 0x04 | Azure |
| 0x05 | Turquoise |
| 0x06 | Green |
| 0x07 | Yellow |
| 0x08 | Orange |
| 0x09 | Red |
| 0x0A | White |
| 0xFF | Unknown |

### Program Actions:
| Value | Action |
|-------|--------|
| 0x00 | Start |
| 0x01 | Stop |

### Response Status:
| Value | Status |
|-------|--------|
| 0x00 | Acknowledged |
| 0x01 | Not Acknowledged |

### Motor End States:
| Value | State |
|-------|-------|
| 0x00 | Coast |
| 0x01 | Brake |
| 0x02 | Hold |
| 0x03 | Continue |
| 0x04 | Coast (smart) |
| 0x05 | Brake (smart) |
| 0xFF | Default |

### Motor Move Directions:
| Value | Direction |
|-------|-----------|
| 0x00 | Clockwise |
| 0x01 | Counter-Clockwise |
| 0x02 | Shortest Path |
| 0x03 | Longest Path |

## DEVICE NOTIFICATION SUB-MESSAGES

### DeviceBattery (0x00):
- uint8: battery level percent

### DeviceImuValues (0x01):
- uint8: face up, uint8: yaw face
- int16 x6: yaw, pitch, roll, accel_x, accel_y, accel_z
- int16 x3: gyro_x, gyro_y, gyro_z

### Device5x5MatrixDisplay (0x02):
- uint8[25]: pixel values

### DeviceMotor (0x0A):
- uint8: port, uint8: device_type
- int16: absolute_position (-180 to 179)
- int16: power (-10000 to 10000)
- int8: speed (-100 to 100)
- int32: position

### DeviceForceSensor (0x0B):
- uint8: port, uint8: value (0-100), uint8: pressed (0/1)

### DeviceColorSensor (0x0C):
- uint8: port, int8: color
- uint16 x3: raw_red, raw_green, raw_blue (0-1023)

### DeviceDistanceSensor (0x0D):
- uint8: port, int16: distance_mm (40-2000, -1 if none)

### Device3x3ColorMatrix (0x0E):
- uint8: port, uint8[9]: pixels (high nibble=brightness, low nibble=color)

## PROGRAM SLOTS
- 20 slots available, indexed 0-19
- Programs stored as files with names up to 31 chars + null terminator

## KEY INSIGHT: InfoRequest MUST be first message
The official docs state: "Upon connecting, the client should always initiate communication
by sending an InfoRequest to the hub." This gives us max_packet_size and max_chunk_size.
etomasfe SKIPS this but hardcodes chunk sizes instead.
