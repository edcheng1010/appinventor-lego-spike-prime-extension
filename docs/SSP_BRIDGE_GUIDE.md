> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../NOTICE) for trademark and licensing details.*

# SSP v0.8 Bridge Guide — LEGO SPIKE Prime

This document maps every App Inventor block to its [SSP v0.8](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.8.md) command/event and describes the transport profile used by this bridge. The App Inventor extension is the **reference client** for the `spike-prime-3.x` profile; the Scratch (TurboWarp) and TypeScript clients implement the same vocabulary.

---

## Transport Profile

This bridge implements the `spike-prime-3.x` transport profile (SSP v0.8 §2.1):

| Field | Value |
|---|---|
| `profile_id` | `spike-prime-3.x` |
| `transport` | `ble` |
| `discovery.service_uuid` | `0000fd02-0000-1000-8000-00805f9b34fb` |
| `framing` | `cobs-xor` — COBS encode, XOR each byte with `0x03`, frame `0x01 … 0x02` |
| `wrapper` | TunnelMessage opcode `0x32` — `[0x32][size_low][size_high][payload]` |
| `ssp_payload_encoding` | `json-utf8-newline` |

All SSP JSON messages are UTF-8 strings terminated by `\n`, wrapped in TunnelMessage, COBS-encoded. BLE characteristics: RX (client→hub, write) `0000fd02-0001-…`, TX (hub→client, notify) `0000fd02-0002-…`.

---

## Connection Lifecycle

```
App Inventor                          SPIKE Prime Hub
      |                                     |
      |-- BLE connect ------------------>   |
      |-- InfoRequest (0x00) ----------->   |
      |<- InfoResponse (0x01) ----------    |   adopt maxPacketSize / maxChunkSize
      |                                     |
      | [if first connection / new hash]    |
      |-- ClearSlot (0x46) ------------->   |
      |-- StartFileUpload (0x0C) ------->   |
      |-- TransferChunk (0x10) x N ----->   |
      |-- ProgramFlow start (0x1E) ----->   |
      |                                     |
      | [on reconnect: hash match → skip]   |
      |-- ProgramFlow start (0x1E) ----->   |
      |                                     |
      |<- {"type":"capability",...} ------  |   HubConnected fires (capability folded in)
      |                                     |
      |-- {"cmd":"motor.run",...}\n ----->  |
      |<- {"event":"sensor",...}\n -------  |
      |-- {"cmd":"system.ping"}\n ------->  |   every 5 s (heartbeat)
      |<- {"event":"pong"}\n -------------  |
```

`HubConnected` fires only **after** the capability declaration is received, so its parameters carry the capability fields directly (no separate "capability received" event).

---

## Capability Declaration

On startup the hub program emits one capability message. Motor ports A–F are enumerated dynamically based on what is physically connected.

```json
{
  "type": "capability",
  "device": "spike-prime",
  "firmware": "3.x",
  "ssp_version": "0.8",
  "encodings": ["json-utf8-newline"],
  "supports_batch": false,
  "tank_drive": true,
  "system_metrics": ["battery", "charging", "temperature",
                     "button.left", "button.right", "button.center"],
  "ports": [
    {"id":"A","type":"motor",
     "features":["speed","position","stall","power","acceleration"],
     "goto_modes":["absolute","relative"],
     "constraints":{"speed":{"type":"int","min":-100,"max":100},
                    "position":{"type":"int","min":0,"max":359,"wraps":true},
                    "acceleration":{"type":"int","min":0,"max":10000}}},
    {"id":"display","type":"display","width":5,"height":5,"depth":"grayscale",
     "features":["pixel","image","text","brightness","orientation"]},
    {"id":"status","type":"led","features":["set"],
     "constraints":{"color":{"type":"enum","values":["azure","black","blue","cyan",
        "green","magenta","orange","red","violet","white","yellow","off"]}}},
    {"id":"imu","type":"orientation",
     "features":["pitch","roll","yaw","gesture","face_orientation","angular_velocity"],
     "constraints":{"gesture":{"type":"enum","values":["shake","tap","double_tap","fall"]},
                    "face_orientation":{"type":"enum","values":["face_up","face_down",
                      "port_a_up","port_a_down","port_e_up","port_e_down"]}}},
    {"id":"speaker","type":"speaker","features":["beep","volume"],
     "sound_wait_supported":true}
  ]
}
```

The status LED declares an **enum** color constraint (not RGB), per SSP v0.8 §5.1 — `led.set` takes a color name string.

---

## Block → SSP Mapping

Conventions: `port` is a Designer/blocks property unless shown as a parameter. One-shot reads carry a `request_id`; the hub echoes it on the matching `sensor` event so the response routes to the right getter.

### LegoSpikeConnectivity

| Block | SSP / transport |
|---|---|
| `StartScanning` / `StopScanning` | BLE scan for service `0000fd02-…` |
| `HubCount` / `HubName(index)` | Local — visible-hub list |
| `ConnectToHub(index)` | BLE connect + upload `hub_controller.py` if hash differs |
| `Disconnect` | BLE disconnect |
| `HubConnected(deviceName, deviceType, sspVersion, availablePorts, supportedEncodings)` | Fired after `{"type":"capability",…}`; params are capability fields |
| `HubDisconnected` | BLE drop **or** no `pong` within 10 s (heartbeat lost) |
| `ErrorOccurred(message)` | `{"event":"error",…}` or local error |
| Heartbeat (internal) | `{"cmd":"system.ping"}` every 5 s → `{"event":"pong"}` |

### LegoSpikeMotors  (`Port`, `Direction` properties)

| Block | SSP command |
|---|---|
| `StartMotor` | `{"cmd":"motor.run","port":P,"speed":±s}` (or `"mode":"power"` after `SetMotorPower`) |
| `StopMotor` | `{"cmd":"motor.stop","port":P,"stop_action":a}` |
| `SetMotorSpeed(v)` / `SetMotorPower(v)` | Local — applied on next `StartMotor` |
| `SetMotorBrakeAtStop(action)` | Local — applied on next `StopMotor` |
| `RunMotorForDuration(amount, unit)` | `{"cmd":"motor.run","port":P,"speed":±s,"duration":amount,"duration_unit":"ms"|"degrees"|"rotations"}` |
| `GoToMotorAbsolutePosition(pos)` | `{"cmd":"motor.goto","port":P,"position":0–359,"speed":s,"mode":"absolute"}` |
| `GoToMotorRelativePosition(deg)` | `{"cmd":"motor.goto","port":P,"position":±deg,"speed":s,"mode":"relative"}` |
| `ResetRelativeMotorPosition` | `{"cmd":"motor.reset","port":P}` |
| `SetMotorAcceleration(rate)` | `{"cmd":"motor.set_acceleration","port":P,"rate":0–10000}` |
| `GetMotorRelativePosition` | `{"cmd":"sensor.read","port":P,"type":"position"}` → `MotorRelativePositionRead(port, degrees)` |
| `GetMotorAbsolutePosition` | `… "type":"absolute_position"` → `MotorAbsolutePositionRead(port, degrees)` |
| `GetMotorSpeed` | `… "type":"speed"` → `MotorSpeedRead(port, speed)` |

Speed is −100…+100 (the hub scales ×11 to firmware velocity). `Direction = Counterclockwise` negates speed/position.

### LegoSpikeMovement  (`LeftMotorPort`, `RightMotorPort`, `Direction` properties)

| Block | SSP command |
|---|---|
| `SetMovementPair` | `{"cmd":"movement.configure","left":L,"right":R}` (sent immediately) |
| `SetMovementSpeed(v)` | Local — applied on next move |
| `StartMoving` | `{"cmd":"movement.drive","left":L,"right":R,"speed":±s,"steering":0}` |
| `StartMovingWithSteering(st)` | `… "steering":−100…100}` |
| `StartMovingAtSpeed(l, r)` | `{"cmd":"movement.drive","left":L,"right":R,"left_speed":±l,"right_speed":±r}` (tank) |
| `MoveForDuration(amount, unit)` | `… "steering":0,"duration":amount,"duration_unit":…}` |
| `MoveWithSteeringForDuration(st, amount, unit)` | `… "steering":st,"duration":amount,"duration_unit":…}` |
| `StopMoving` | `{"cmd":"movement.stop","stop_action":a}` |
| `SetMovementBrakeAtStop(mode)` | Local — applied on next `StopMoving` |
| `SetMovementRotationDistance(cm)` | Local — cm per wheel rotation (for `rotations` unit) |
| `SetMovementAcceleration(rate)` | `{"cmd":"movement.set_acceleration","rate":0–10000}` |

### LegoSpikeLight  (`Image` property)

| Block | SSP command |
|---|---|
| `TurnOnLightMatrix` | `{"cmd":"led.matrix.image","port":"display","image":IMG}` |
| `TurnOnLightMatrixForSeconds(s)` | `led.matrix.image`, then client-timed `led.matrix.clear` |
| `TurnOffLightMatrix` | `{"cmd":"led.matrix.clear","port":"display"}` |
| `WriteOnLightMatrix(text)` | `{"cmd":"led.matrix.text","port":"display","text":t}` |
| `SetPixelBrightness(x, y, b)` | `{"cmd":"led.matrix.pixel","port":"display","x":0–4,"y":0–4,"brightness":0–100}` (block x/y are 1–5) |
| `SetLightMatrixBrightness(level)` | `{"cmd":"led.matrix.brightness","port":"display","level":0–100}` |
| `RotateLightMatrix(degrees)` | `{"cmd":"led.matrix.rotate","port":"display","degrees":d}` *(profile extension, §11)* |
| `SetLightMatrixOrientation(rotation)` | `{"cmd":"led.matrix.orientation","port":"display","rotation":0/90/180/270}` |
| `SetCenterButtonLight(color)` | `{"cmd":"led.set","port":"status","color":name}` |

Valid `Image` names: HAPPY, SAD, SMILE, HEART, HEARTSMALL, CONFUSED, ANGRY, ASLEEP, SURPRISED, YES, NO, ARROWNORTH/EAST/SOUTH/WEST.

### LegoSpikeSensors  (`ColorSensorPort`, `DistanceSensorPort`, `ForceSensorPort` properties)

| Block | SSP command | Event |
|---|---|---|
| `GetColor` | `sensor.read type=color` | `ColorRead(port, color)` |
| `GetColorRGB` | `sensor.read type=rgb` | `ColorRGBRead(port, r, g, b)` |
| `GetReflectedLight` | `sensor.read type=reflected` | `ReflectedLightRead(port, value)` |
| `GetDistance` | `sensor.read type=distance` | `DistanceRead(port, mm)` |
| `GetForce` | `sensor.read type=force` | `ForceRead(port, value)` |
| `GetHubTiltAngle(axis)` | `sensor.read port=imu type=pitch|roll|yaw` | `HubTiltAngleRead(axis, degrees)` |
| `GetHubAcceleration` | `sensor.read port=imu type=acceleration` | `HubAccelerationRead(x, y, z)` |
| `GetHubAngularVelocity` | `sensor.read port=imu type=angular_velocity` | `HubAngularVelocityRead(x, y, z)` |
| `GetHubOrientation` | 3× `sensor.read` pitch/roll/yaw | `HubOrientationRead(pitch, roll, yaw)` (once all three arrive) |
| `GetHubFaceOrientation` | `sensor.read port=imu type=face_orientation` | `HubFaceOrientationRead(faceOrientation)` |
| `GetHubTimer` | `timer.get` *(ext §11)* | `HubTimerRead(seconds)` |
| `ResetHubTimer` | `timer.reset` *(ext §11)* | — |
| `ResetHubYaw` | `orientation.reset_yaw` | — |
| `SetHubYaw(deg)` | `orientation.set_yaw angle=deg` | — |
| `SetHubOrientation(face)` | `orientation.set_reference face=…` | — |
| `SubscribeToHubLeftButton` / `…Right` | `system.subscribe metric=button.left|right interval=100` | `WhenHubButtonPressed/Released(button)` |
| `SubscribeToHubGestures` | `sensor.subscribe port=imu type=gesture mode=on_change` | `HubGestureDetected(gesture)` |
| `SubscribeToHubFaceOrientation` | `sensor.subscribe port=imu type=face_orientation mode=on_change` | `HubFaceOrientationChanged(faceOrientation)` |
| `LightUpDistanceSensor(tl,tr,bl,br)` | `led.distance port=<dist> tl/tr/bl/br=0–100` *(ext §11)* | — |

**Predicate (boolean) checks** — hub-side comparison, profile extensions (SSP v0.8 §11):

| Block | SSP command | Event |
|---|---|---|
| `IsColor(color)` | `sensor.read type=is_color color=…` | `ColorChecked(port, color, isMatch)` |
| `IsCloserThan(mm)` | `sensor.read type=is_closer mm=…` | `DistanceChecked(port, isCloser)` |
| `IsReflectedLightAbove(pct)` | `sensor.read type=is_reflected_above percent=…` | `ReflectedLightChecked(port, isAbove)` |
| `IsForceSensorChecked` | `sensor.read type=touched` | `ForceSensorChecked(port, isPressed)` |
| `IsTilted(direction)` | `sensor.read port=imu type=is_tilted direction=…` | `HubTiltChecked(direction, isTilted)` |
| `IsHubOrientation(face)` | `sensor.read port=imu type=is_orientation face=…` | `HubOrientationChecked(face, isMatch)` |
| `IsShaking` | `sensor.read port=imu type=is_shaking` | `HubShakingChecked(isShaking)` |
| `IsHubButtonPressed(button)` | `system.read metric=is_button_pressed button=…` | `HubButtonChecked(button, isPressed)` |

### LegoSpikeSound

| Block | SSP command |
|---|---|
| `Beep(freq, durationMs)` | `{"cmd":"sound.beep","freq":f,"duration":d}` (non-blocking) |
| `PlayBeepForSeconds(freq, s)` | `sound.beep` with `duration = s×1000` |
| `StartPlayingBeep(freq)` | `{"cmd":"sound.beep","freq":f}` (no duration — runs until stopped) |
| `StopAllSounds` | `{"cmd":"sound.stop"}` |
| `SetVolume(level)` | `{"cmd":"sound.set_volume","level":0–100}` |
| `GetVolume` | `{"cmd":"sound.read","metric":"volume"}` → `VolumeRead(level)` |

### LegoSpikeSystem

| Block | SSP / transport | Event |
|---|---|---|
| `GetBatteryLevel` | `system.read metric=battery` | `BatteryLevelRead(percent)` |
| `GetTemperature` | `system.read metric=temperature` | `TemperatureRead(celsius)` |
| `IsCharging` | `system.read metric=charging` | `ChargingStateRead(charging)` |
| `GetRSSI` | **Transport** — `BluetoothLE.ReadConnectedRssi()` (not SSP; hub cannot read its own RSSI) | `RSSIRead(rssi)` |

### LegoSpikeMusic  (tempo is client-side)

| Block | SSP command |
|---|---|
| `PlayNoteForBeats(note, beats)` | `{"cmd":"sound.beep","freq":f,"duration":beats×60000/tempo,"wait":true}` |
| `RestForBeats(beats)` | `{"cmd":"sound.rest","duration":beats×60000/tempo}` *(ext §11)* |
| `SetTempo(bpm)` / `ChangeTempo(delta)` / `GetTempo` | Local |

`note` is a `MusicNote` enum (C3–C7, sharps as `Csharp4`); converted to frequency `440 × 2^((midi−69)/12)`.

---

## Sensor / System Event Formats

```json
{"event":"sensor","port":"C","type":"color","value":"red","request_id":"r12"}
{"event":"sensor","port":"B","type":"distance","value":235}
{"event":"sensor","port":"C","type":"rgb","value":[120,40,30]}
{"event":"sensor","port":"imu","type":"pitch","value":12}
{"event":"sensor","port":"imu","type":"acceleration","value":{"x":0.01,"y":-0.02,"z":1.0}}
{"event":"sensor","port":"imu","type":"face_orientation","value":"Top"}
{"event":"sensor","port":"imu","type":"is_tilted","value":{"tilted":true,"direction":"forward"}}
{"event":"sensor","port":"timer","type":"elapsed","value":42}
{"event":"system","metric":"battery","value":87}
{"event":"system","metric":"button.left","value":"pressed"}
{"event":"system","metric":"is_button_pressed","value":{"button":"left","pressed":true}}
{"event":"pong"}
{"event":"error","code":201,"message":"Unknown port: G","request_id":"r12"}
```

---

## Profile Extensions

The bridge implements several SSP v0.8 **profile extensions** (registered in spec §11), so the bare command names are interoperable across clients: predicate sensor reads (`is_*`), `system.read metric=is_button_pressed`, `timer.get`/`timer.reset`, `led.distance`, `led.matrix.rotate`, `sound.rest`. See [SSP-v0.8 §11](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.8.md#11-appendix--profile-extensions-spike-prime-3x).

`system.dfu` is accepted but returns error `501` (firmware update not supported by this bridge).

---

## Error Codes (SSP v0.8 §7)

| Code range | Category | Example |
|---|---|---|
| 100–199 | Connection errors | — |
| 200–299 | Command errors | `201 Unknown port` |
| 300–399 | Hardware errors | `301 Motor error` |
| 400–499 | Protocol errors | `400 Malformed JSON` |
| 500–599 | Unsupported | `501 system.dfu not supported` |

Errors surface on `LegoSpikeConnectivity.ErrorOccurred(message)`.

---

## Heartbeat

The extension sends `{"cmd":"system.ping"}` every 5 seconds once connected; the hub responds `{"event":"pong"}`. If no pong arrives within 10 seconds, `HubDisconnected` fires once and the heartbeat stops. (Pressing the hub's center button terminates the hub Python program at firmware level — `HubDisconnected` then fires ~10 s later.)

---

## Notes for Future Bridge Implementations

To add a new hardware platform following the Solaria Type 2 pattern:

1. Define a transport profile (SSP v0.8 §2.1) for the hardware's BLE/Serial framing.
2. Write a hub-side program (or firmware) that emits a capability declaration on startup and handles SSP JSON commands. Register any non-standard commands in a profile-extension appendix or `x_`-prefix them (§5.1).
3. Create a client (`.aix`, Scratch extension, Python lib, …) that speaks the SSP vocabulary over the new profile.
4. See the [solaria-hub ARCHITECTURE.md](https://github.com/edcheng1010/solaria-hub/blob/main/ARCHITECTURE.md) for the Type 1 / Type 2 hybrid model, and the [SSP Client Contract](https://github.com/edcheng1010/solaria-lib-spike-prime/blob/main/spec/SSP-CLIENT-v0.8.md) for the transport + lifecycle conformance checklist.
