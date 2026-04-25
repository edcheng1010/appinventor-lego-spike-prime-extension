# etomasfe/SpikeRemoteControl — Line-by-Line Analysis

## THIS IS THE ONLY PROVEN WORKING IMPLEMENTATION FOR REMOTE MOTOR CONTROL

## Connection Flow (EXACT SEQUENCE)
1. Request Bluetooth device with filter: `services: [SPIKE_SERVICE_UUID]`
2. Connect to GATT server
3. Get primary service (SPIKE_SERVICE_UUID)
4. Get RX characteristic (SPIKE_RX_UUID) — for writing TO hub
5. Get TX characteristic (SPIKE_TX_UUID) — for reading FROM hub
6. Start notifications on TX
7. **clear()** — Send ClearSlotRequest
8. **sendFileUpload()** — Send StartFileUploadRequest with program CRC
9. **transferChunk()** — Send program in chunks (hardcoded 3 chunks of 445 chars each)
10. **sendCommandStart()** — Send ProgramFlowRequest to start program
11. **waitForReady()** — Wait for hub to send "rdy" via tunnel

## UUIDs (CONFIRMED — matches LEGO official)
```javascript
SPIKE_SERVICE_UUID = "0000fd02-0000-1000-8000-00805f9b34fb"
SPIKE_RX_UUID = "0000fd02-0001-1000-8000-00805f9b34fb"
SPIKE_TX_UUID = "0000fd02-0002-1000-8000-00805f9b34fb"
```

## COBS Constants (CONFIRMED — matches LEGO official)
```javascript
DELIMITER = 0x02
NO_DELIMITER = 255 (0xFF)
MAX_BLOCK_SIZE = 84
COBS_CODE_OFFSET = 2
XOR = 0x03
```

## THE PYTHON CONTROLLER PROGRAM (THE KEY TO EVERYTHING)
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

## CRITICAL: TunnelMessage Format
```javascript
async function sendCommand(command) {
    data = encoder.encode("\x32\x0a\x00" + command);
    data2 = pack(data);
    await commandCharacteristicRX.writeValue(data2);
}
```
### TunnelMessage (0x32) structure:
- Byte 0: `0x32` — Message ID (TunnelMessage)
- Byte 1: `0x0a` — Size low byte (10 = length of command string)
- Byte 2: `0x00` — Size high byte
- Bytes 3+: Command string (UTF-8 encoded)

### WAIT — the size bytes (0x0a, 0x00) are HARDCODED to 10!
This means the command format is ALWAYS 10 characters:
- Format: `{port1}{sign}{3digits}{port2}{sign}{3digits}`
- Example: `A+050B+050` (Motor A at +50, Motor B at +50)
- The power values are multiplied by 10 on the hub side: `mA=int(n)*10`

### Command Protocol:
- `A+050B+050` = Motor A forward 500 deg/s, Motor B forward 500 deg/s
- `A-050B+050` = Motor A reverse 500 deg/s, Motor B forward 500 deg/s
- `A+000B+000` = Stop both motors
- `bye.bye.AB` = Quit the program on the hub

## CRITICAL: The "rdy" Handshake
After each command, the hub Python program sends `tunnel.send(b'rdy')` back.
The JavaScript waits for this before sending the next command.
This is a simple flow-control mechanism to prevent command flooding.

## File Upload Details
### ClearSlotRequest:
```javascript
data = encoder.encode("\x46\x00");  // Clear slot 0
data2 = pack(data);
```

### StartFileUploadRequest:
```javascript
name = "program.py\0"
num = "\x00"  // slot 0
crc = crc32Uint8Array(codi, 0, true);  // CRC of full program
crc_2 = uint32ToUint8Array(crc).reverse();  // Little-endian!
data = [0x0C] + name + num + crc_2
data = pack(data);
```

### TransferChunkRequest:
```javascript
// Chunk 1: first 445 chars
codi = encoder.encode(part1.substring(0, 445));
tamany = uint16ToUint8Array(codi.length);  // Little-endian uint16
crc = crc32Uint8Array(codi, 0, true);  // Running CRC starts at 0
crc_2 = uint32ToUint8Array(crc).reverse();  // Little-endian!
data = [0x10] + crc_2 + tamany + codi
data = pack(data);

// Chunk 2: chars 445-890
crc = crc32Uint8Array(codi2, crc, true);  // Running CRC uses previous CRC as seed
// ... same pattern

// Chunk 3: chars 890+
crc = crc32Uint8Array(codi3, crc, true);  // Running CRC continues
```

### ProgramFlowRequest (Start):
```javascript
data = encoder.encode("\x1e\x00\x00");  // [0x1E, stop=0, slot=0]
data2 = pack(data);
```

## CRITICAL OBSERVATIONS:
1. **No InfoRequest!** etomasfe skips the InfoRequest step entirely. They hardcode chunk sizes.
2. **Hardcoded chunk size of 445 characters** — this works but is not dynamic
3. **CRC is reversed** (`.reverse()`) for little-endian byte order
4. **The tunnel message size is hardcoded to 0x0a (10)** — not dynamic
5. **The "rdy" handshake is critical** — without it, commands may be lost
6. **The program uses `while True: pass`** to keep running indefinitely
7. **`tunnel.callback(receive_tunnel_message)`** registers the callback
8. **`tunnel.send(b'rdy')`** is called once at startup to signal readiness
9. **`quit()`** is called when "bye.bye.AB" is received to cleanly exit
