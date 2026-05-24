# Implementation Plan — LEGO SPIKE Prime App Inventor Extension

**Last revised:** 2026-05-24
**Targeting:** SSP v0.6
**Status:** Phase 1 complete · Phase 2 next
**Author:** Edward Cheng

---

## Overview

This document is the working roadmap for the extension. It consolidates:
- The original pre-MVP plan (now Phase 1, complete)
- The post-MVP block expansion (now Phase 3, from `mvp_status_and_postmvp.md`)
- The SSP migration work (PR 1 = Phase 2; PR 2 = Phase 4)
- Multi-hub support (Phase 5)

Five phases, executed in order. Nothing from the prior plans has been dropped — see the "Inheritance from prior plans" section at the bottom for traceability.

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation — BLE, COBS, TunnelMessage, custom binary protocol, 5 working components | ✅ Complete |
| 2 | SSP v0.6 migration (PR 1) — same blocks, SSP wire format | ⏳ Next |
| 3 | Post-MVP block expansion — Sound, System, Music, IMU, full motor/movement/light/sensor blocks | After Phase 2 |
| 4 | Client/bridge architectural split (PR 2) — `TransportProfile`, bridge extraction, download-on-connect | After Phase 3 |
| 5 | Multi-hub support — LWP (Boost / SPIKE Essential / Technic), EV3 via RFCOMM, generic bridges | Long-term |

---

## Decisions made (2026-05-24, still in force)

1. **Validation strategy:** Client-side validation fires `OnError` event and refuses to send. Out-of-range parameters surface immediately rather than silently clamping or waiting for a bridge round-trip.
2. **JSON parse performance:** Commit to JSON for Phase 2. Perf benchmark gate before merging — sustained 20 Hz movement updates for 60 s with no payload drops. If perf fails, fall back to v0.6 §3.2 binary encoding (now fully spec'd); mitigate temporarily by throttling movement updates to 10 Hz.
3. **Bridge program embedding:** End state is download-on-connect (versioned bridge releases, decoupled from the .aix). Implement during Phase 4. Phase 2 keeps the bridge program embedded as a Java string.
4. **Phase ordering:** Phase 2 before Phase 3. Doing post-MVP block expansion *after* SSP migration avoids duplicating new commands in two wire formats.

---

## SSP version targeting

This plan targets **SSP v0.6** as the spec stands today. v0.6 absorbed every blocker we'd surfaced from the SPIKE Prime bridge perspective:

| Wishlist | Status | Notes |
|---|---|---|
| v0.2 | ✅ Integrated | Transport profiles, binary encoding reserved, movement category, capability schema, request_id, heartbeat, sensor flow-control |
| v0.3 | ✅ Integrated | led.matrix, display port, orientation port, sound.play payloads, speaker port, system.subscribe |
| v0.4 | ✅ Integrated | Parameter constraints (`int`/`float`/`enum`/`bool`), gesture event consistency |
| v0.5 | ✅ Integrated | Button format formalised, `array` + `string` constraints, gesture constraint enum, implicit-coordinate-constraints from port dimensions |
| v0.6 | ✅ Integrated | RFCOMM transport, binary encoding finalised, batch commands, motor duration / stop_action, sound.set_volume, led.matrix.brightness / .orientation |
| v0.7+ | 📝 Future | DFU, stream multiplexing, auth — not needed for SPIKE Prime bridge |

**No outstanding wishlist dependencies for Phases 2–5.** The bridge can ship fully v0.6-compliant with zero `x_` extensions.

Potential v0.7 candidates surfaced while planning Phase 3 (not yet filed):
- `display` port on sensors (e.g., the SPIKE distance sensor's 4-LED indicator — `LightUpDistanceSensor` block)
- `SetMotorAcceleration` as canonical motor feature
- `ResetYaw` as orientation port command (currently no analogue in spec)
- 3×3 color sensor matrix accessory (would need `display` port type `rgb` with `width:3, height:3`)
- Music/MIDI semantics (notes string parsing, drum-kit specification)

---

## Phase 1 — Foundation *(COMPLETE ✓)*

Originally the pre-MVP "Protocol Correction" plan in CLAUDE.md.

**Shipped:**
- BLE scan with RSSI staleness ghost-filtering (CLAUDE.md Rule 2)
- COBS encoding with verified constants — delimiter `0x02`, XOR `0x03`, MAX_BLOCK_SIZE 84
- CRC32 with running-CRC support and 4-byte alignment
- Message framing: COBS → XOR → delimiter
- File upload protocol: `ClearSlot` → `StartFileUpload` → `TransferChunk` → `ProgramFlow`
- TunnelMessage send/receive (opcode `0x32`)
- Hub-side Python controller program embedded in Java as string constant
- Auto-upload of controller program on connection
- 5 working App Inventor components: Connectivity, Motors, Movement, Light, Sensors
- Custom `MTR` / `MOV` / `LGT` / `SEN` binary command protocol over TunnelMessage
- BLE connection tested on physical SPIKE Prime 3.x hubs in classroom conditions
- Reconnect-after-disconnect tested (RSSI staleness solves ghost-device cache)

**Known limitations carrying into Phase 2:**
- Center button LED is owned by firmware during BLE/TunnelMessage mode — cannot be re-coloured live during a running tunnel; block removed
- `hub.motion_sensor.tilt_angles()` may return 0 on some firmware revisions (IMU fallback)
- `BluetoothLE.BytesReceived → LegoSpikeConnectivity.OnBytesReceivedFromHub` must be wired manually (`BluetoothLE` extension's auto-wiring fails on the version we depend on)
- `color.CYAN` / `color.VIOLET` not defined in firmware's color module; integer fallback works
- BLE perf: maxChunkSize 960 bytes, maxPacketSize 20 bytes (RequestMTU removed — caused TX subscription invalidation)

---

## Phase 2 — SSP v0.6 migration (PR 1)

**Goal:** swap the wire protocol from custom binary `MTR/MOV/LGT/SEN` to SSP v0.6. The App Inventor block API surface stays identical from the user's perspective. Internal protocol becomes hardware-agnostic.

**Effort:** ~2 weeks one developer. Dominated by the hub-side Python rewrite and end-to-end physical-hub testing.

**Acceptance criteria:**
- Every existing block produces equivalent behaviour on a physical hub
- Bridge emits SSP v0.6 capability declaration on connect, with constraints per §5.2
- Sustained 20 Hz movement updates work (decision #2 — or perf-gate fallback to binary encoding)
- New `OnError` event fires for out-of-range parameters before any bytes go on wire
- Heartbeat (5 s ping, 10 s disconnect on missed pongs) works end-to-end
- No regression in connection stability vs current MVP
- All connection/upload/reconnection tests from the original CLAUDE.md Phase 3 still pass

### 2.1 Hub-side Python rewrite

- **2.1.1** Replace `MTR/MOV/LGT/SEN` binary parser with `json.loads()` on newline-delimited frames
- **2.1.2** Implement command dispatcher for SPIKE-relevant SSP commands:
  - `motor.run`, `motor.stop`, `motor.goto`, `motor.reset`
  - `movement.configure`, `movement.drive`, `movement.turn`, `movement.stop`
  - `led.set` (status LED), `led.off`
  - `led.matrix.pixel`, `led.matrix.image`, `led.matrix.text`, `led.matrix.clear`
  - `sound.beep`, `sound.stop`
  - `sensor.subscribe`, `sensor.unsubscribe`, `sensor.read`
  - `system.ping` (→ `pong`), `system.info`, `system.subscribe`, `system.read`, `system.unsubscribe`
- **2.1.3** Capability declaration builder — emit on tunnel-ready:
  - Enumerate motor ports (A–F) by inspecting `hub.port.<id>` for `Motor` instances
  - Enumerate sensor ports (color, distance, force) by device type
  - Always declare virtual ports: `display`, `status`, `imu`, `speaker`
  - Always declare `system_metrics` array: battery, charging, temperature, button.left/right/center, connection_rssi
  - Set `ssp_version: "0.6"`, `encodings: ["json-utf8-newline"]`
  - Include canonical constraints per v0.6 §5.2 (full table in 2.1.7 below)
  - Light matrix declares `features: ["pixel","image","text"]` initially (brightness/orientation added in Phase 3)
  - Speaker declares `features: ["beep"]` initially (builtin/midi/volume added in Phase 3 after FW capability verification)
- **2.1.4** `SubscriptionManager` class — single async loop, emits `{"event":"sensor",...}` per registered subscription, honors `mode`/`interval`/`min_change` per v0.6 §6.5
- **2.1.5** Heartbeat handler — respond to `system.ping` with `pong` event; auto-disconnect after 10 s without ping (only when subscriptions are active)
- **2.1.6** Gesture event emit in v0.6 sensor-form: `{"event":"sensor","port":"imu","type":"gesture","value":"shake"}`
- **2.1.7** Structured error events per v0.6 §7 with `request_id` echo

**Phase-2 constraints to declare** (matches v0.6 §5 SPIKE example):

| Port | Feature | Constraint |
|---|---|---|
| A–F motor | `speed` | `{"type":"int","min":-100,"max":100}` |
| A–F motor | `position` | `{"type":"int","min":0,"max":359,"wraps":true}` |
| `status` led | `color` | `{"type":"enum","values":["red","orange","yellow","green","cyan","blue","violet","magenta","white","off"]}` — matches `hub.light.color()` palette |
| `imu` orientation | `pitch`/`roll` | `{"type":"int","min":-180,"max":180}` |
| `imu` orientation | `yaw` | `{"type":"int","min":0,"max":360,"wraps":true}` |
| `imu` orientation | `gesture` | `{"type":"enum","values":["shake","tap","double_tap","fall","face_up","face_down"]}` |
| sensor `subscribe.interval` | minimum | `{"type":"int","min":50}` |
| `display.brightness` (per-pixel) | range | implicit via `depth:"grayscale"` → `[0,100]` |
| `display.x`, `display.y` | range | implicit via `width:5`/`height:5` → `[0,4]` |

### 2.2 Java side new infrastructure

- **2.2.1** `SSPMessage.java` — `JSONObject`-based command builder with fluent API (`withRequestId(id)`, `withPort(port)`, `withParam(key, value)`); serialises to `bytes + \n`
- **2.2.2** `SSPParser.java` — parses incoming newline-delimited JSON frames; dispatches to typed listeners (`onCapability`, `onSensor`, `onSystem`, `onError`, `onPong`)
- **2.2.3** `SSPClient.java` — owns COBS+TunnelMessage transport via existing `BluetoothInterfaceImpl`; exposes `send(SSPMessage)`, `setListener(SSPListener)`, manages `request_id` auto-incrementing counter
- **2.2.4** `SpikeTransportProfile.java` — static profile constant matching v0.6 §2.1 SPIKE example exactly (FD02 UUID, cobs-xor framing, TunnelMessage 0x32 wrapper, json-utf8-newline encoding)
- **2.2.5** `CapabilityStore.java` — caches parsed v0.6 capability data; queryable (`getDeviceType()`, `getPortType(portId)`, `getConstraint(portId, feature, attribute)`, `hasFeature(portId, feature)`, `getSystemMetrics()`, `supportsBatch()`)
- **2.2.6** `Validator.java` — consults `CapabilityStore` to validate `SSPMessage` before send; throws `ValidationException` for out-of-range / unsupported-feature / unknown-port; called by `SSPClient.send()`. Rate-limits `OnError` events to one per (port, parameter) per second to avoid classroom noise.
- **2.2.7** `HeartbeatManager.java` — 5 s ping timer, lifecycle-managed (started on first subscribe, stopped on last unsubscribe); fires `OnHeartbeatLost` after 2 missed pongs
- **2.2.8** Client-side `SubscriptionManager.java` — tracks active subscriptions per port, caches last sensor value for one-shot getters

### 2.3 Component migration

One commit per component, in this order (Connectivity must land first — others depend on `CapabilityStore`):

- **2.3.1** `LegoSpikeConnectivity`:
  - Existing `OnBytesReceivedFromHub` rewired through `SSPParser`
  - New events: `OnCapabilityReceived`, `OnError(code, message, requestId)`, `OnHeartbeatLost`
  - New blocks: `GetDeviceType()`, `GetAvailablePorts()`, `GetSupportedEncodings()`, `GetSSPVersion()`
- **2.3.2** `LegoSpikeMotors` — all methods build `SSPMessage` via `SSPClient` instead of binary payloads
- **2.3.3** `LegoSpikeMovement` — same; 1-to-1 with SSP `movement.*`
- **2.3.4** `LegoSpikeLight`:
  - Status LED methods → `led.set` / `led.off`
  - Light matrix methods → `led.matrix.pixel` / `led.matrix.image` / `led.matrix.text` / `led.matrix.clear`
- **2.3.5** `LegoSpikeSensors` — switch from on-demand `read` getters to background subscription:
  - Getter blocks (`GetColor()`, `GetDistance()`, `GetPressure()`, `IsPressed()`, `GetTiltAngle()`) auto-subscribe on first call (mode `interval`, default 100 ms)
  - Return last cached value
  - Explicit `Refresh()` block forces a one-shot `sensor.read`
  - `GetTimer()` / `ResetTimer()` stay client-side (no SSP equivalent needed)
  - All existing `ColorRead` / `DistanceRead` / `PressureRead` / etc. events fire from the subscription stream

### 2.4 Cleanup

- **2.4.1** Delete `MessageBuilder.java` (binary serialiser obsolete)
- **2.4.2** Delete custom `MTR`/`MOV`/`LGT`/`SEN` command constants
- **2.4.3** Update embedded Python program string with the SSP v0.6 dispatcher
- **2.4.4** Update `ARCHITECTURE.md` — Rule 5 ("never send direct motor/LED commands") becomes obsolete; replace with SSP wire-format rules
- **2.4.5** Update `CLAUDE.md` Critical Architectural Rules to reference SSP v0.6
- **2.4.6** Add `docs/SSP_BRIDGE_GUIDE.md` documenting how this bridge maps to SSP v0.6

### 2.5 Testing

Phase 2 must re-run every test from the original CLAUDE.md Phase 3 plus new SSP-specific tests.

- **2.5.1** Unit tests for new Java classes: `SSPMessage`, `SSPParser`, `SSPClient`, `Validator`, `CapabilityStore`, `SubscriptionManager`, `HeartbeatManager`
- **2.5.2** Python-side unit tests on desktop MicroPython runtime:
  - Command dispatcher coverage
  - Subscription manager with all three modes (`interval`/`on_change`/`hybrid`)
  - Capability declaration shape correctness
  - Error event format with/without `request_id`
- **2.5.3** End-to-end integration on physical hub (carries forward CLAUDE.md original tests):
  - Test BLE connection ✓ (original)
  - Test program upload reliability ✓ (original)
  - Test TunnelMessage latency — now JSON-payload perf benchmark, see 2.5.5
  - Test reconnection after disconnect ✓ (original)
  - Each existing block produces same behaviour as MVP
  - Capability declaration matches connected ports when hot-swapping sensors
  - Heartbeat survives an Android screen-lock cycle
- **2.5.4** Connection stability stress: 30-minute classroom-style session with disconnect/reconnect cycles, multiple hubs
- **2.5.5** **JSON perf benchmark gate** (decision #2): 60-second sustained 20 Hz movement updates, measure payload drop rate and latency. Acceptance: <1% drop rate, <100 ms p99 latency. If failed, escalate to v0.6 binary encoding via §3.2.

### 2.6 Risks

- **JSON parse cost on the hub** — mitigated by perf gate + v0.6 §3.2 binary fallback
- **Subscription model changes sensor block semantics** — "get current value" becomes "get last cached value"; mitigated by auto-subscribe-on-first-read and documented in tooltips
- **Capability declaration timing** — ~1 s delay between tunnel-open and capability emit; Connectivity blocks need to gate on `OnCapabilityReceived` rather than `HubConnected`
- **Validator noise** — fixed by per-(port, parameter) per-second rate-limit on `OnError`

---

## Phase 3 — Post-MVP block expansion

**Goal:** add the blocks documented in the post-MVP roadmap (`mvp_status_and_postmvp.md`), built on SSP from day one. Now significantly simplified because v0.6 covers nearly every block with a canonical command.

**Effort:** ~2–3 weeks, paced by App Inventor designer work and end-user testing.

**Acceptance criteria:**
- All blocks in §3.1–§3.7 below available in App Inventor palette
- 7-component architecture (5 existing + Sound + System; possibly 8 with Music)
- Light matrix animation perf benchmark passes (see §3.3)
- `architecture_multicomponent.md` and `mvp_status_and_postmvp.md` memory files updated

### 3.1 `LegoSpikeMotors` expansion

Every block from the post-MVP roadmap, mapped to v0.6 commands:

| Block | SSP v0.6 mapping |
|---|---|
| `RunMotorForDuration(direction, amount, unit)` | `motor.run` with `duration` + `duration_unit` ("ms"/"degrees"/"rotations") — bridge-side timing |
| `MotorGoToPosition(position, direction)` | `motor.goto` with `position` + `direction` |
| `GetMotorPosition()` → `MotorPositionRead` event | `sensor.subscribe` on motor port (`position` feature) |
| `GetMotorSpeed()` → `MotorSpeedRead` event | `sensor.subscribe` on motor port (`speed` feature) |
| `GoToRelativeMotorPosition(degrees)` | `motor.goto` with `relative: true` (verify v0.6 — may need `x_relative_goto` or `motor.run` with `duration_unit:"degrees"`) |
| `ResetRelativeMotorPosition()` | `motor.reset` |
| `RelativeMotorPosition()` | `sensor.read` on motor port (`position` feature) |
| `StartMotorWithPower(power)` | Open question — v0.6 doesn't separate speed/power. Investigate SPIKE FW. Likely `motor.run` with `speed` and `mode:"power"` extension (v0.7 candidate) |
| `MotorPower()` | `sensor.read` on motor port (`load` feature) |
| `StopAndCoastMotor(port)` | `motor.stop` with `stop_action:"coast"` |
| `SetMotorAcceleration(rate)` | Open — no canonical home in v0.6. Use `x_acceleration` extension, file v0.7 wishlist |

### 3.2 `LegoSpikeMovement` expansion

| Block | SSP v0.6 mapping |
|---|---|
| `MoveForDuration(direction, amount, unit)` | `movement.drive` with `duration` + `duration_unit` |
| `MoveWithSteeringForDuration(steering, amount, unit)` | `movement.drive` with `steering` + `duration` |
| `StartMovingAtSpeed(leftSpeed, rightSpeed)` | `movement.drive` with explicit `left_speed` + `right_speed` (verify v0.6 — spec only documents `speed` + `steering`; may need extension or fallback to dual `motor.run`) |
| `SetMotorRotationDistance(distance)` | Client-side config affecting subsequent `movement.drive` `duration` math when `duration_unit:"rotations"` |
| `SetMovementBrakeAtStop(mode)` | Client-side config sticking the `stop_action` parameter onto subsequent `movement.stop` calls |
| `SetMovementAcceleration(rate)` | Same as motor — `x_acceleration` extension until spec'd |

### 3.3 `LegoSpikeLight` expansion (display port)

| Block | SSP v0.6 mapping |
|---|---|
| `SetPixel(x, y, brightness)` | `led.matrix.pixel` |
| `ShowImage(name)` | `led.matrix.image` |
| `ShowText(text, scroll)` | `led.matrix.text` |
| `ClearMatrix()` | `led.matrix.clear` |
| `TurnOnLightMatrixForSeconds(image, seconds)` | `led.matrix.image` then `led.matrix.clear` after delay |
| `SetLightMatrixBrightness(level)` | `led.matrix.brightness` *(v0.6 new)* — bridge adds `brightness` feature on display port |
| `RotateLightMatrix(rotation)` / `SetLightMatrixOrientation(orientation)` | `led.matrix.orientation` *(v0.6 new)* — bridge adds `orientation` feature |
| Light matrix animation (multiple pixels per frame) | `cmd:"batch"` with `atomic:true` per frame — bridge adds `supports_batch: true` to capability |
| `LightUpDistanceSensor(topLeft, topRight, bottomLeft, bottomRight)` | Open — sensor-attached 4-LED indicator. v0.7 candidate as a `display` port on the distance sensor itself (width:2, height:2, depth:grayscale). Ship as `x_distance_led` extension until spec'd. |
| 3×3 Color Matrix accessory blocks | Open — would be a `display` port (3×3 rgb). Ship as separate component (`LegoSpikeColorMatrix`) only if the accessory is in scope; otherwise defer to v0.7+ |

**Acceptance criteria additional:** light matrix animation perf benchmark — 25 pixels updated at 10 Hz, single `batch` command per frame, no payload drops.

### 3.4 `LegoSpikeSensors` expansion (IMU + threshold events)

| Block | SSP v0.6 mapping |
|---|---|
| `GetHubAcceleration()` | `sensor.read` on `imu` port (`acceleration` feature) |
| `GetHubAngularVelocity()` | Open — no canonical feature in v0.6. Use `x_angular_velocity` or compute client-side from pitch/roll/yaw deltas |
| `GetHubOrientation()` | Aggregates pitch/roll/yaw client-side |
| `GetGesture()` | Last cached gesture event value |
| `WhenGesture(type)` | Gesture event from `imu` port; dropdown values populated from v0.6 capability constraint enum |
| `WhenHubShaken` | Convenience block — `WhenGesture("shake")` |
| `ResetYaw()` | Open — no canonical command in v0.6. Use `x_reset_yaw` extension; file v0.7 wishlist |
| `SetHubSensorOrientation(orientation)` | Open — similar, `x_set_orientation` extension |
| `GetRelativeMotorPosition()` | Listed under sensors in roadmap but actually a motor sensor; covered by §3.1 |
| `GetReflectedLight()` | `sensor.read` on color sensor port (`reflected` feature) |
| `IsColor(name)` / `IsDistance(threshold)` / `IsPressed()` | Client-side boolean derived from last subscription value |
| `WhenColorIs` / `WhenCloserThan` / `WhenPressureIs` / `WhenTilted` | `sensor.subscribe` with `mode:"on_change"`; threshold evaluated client-side, event fires when crossing |
| `WhenTimer` | Client-side timer (no SSP equivalent needed) |

### 3.5 `LegoSpikeSound` *(new component)*

Phase-2 bridge declared `speaker` with only `beep`. Phase 3 adds full speaker support after FW capability verification.

| Block | SSP v0.6 mapping |
|---|---|
| `Beep(freq, duration)` | `sound.beep` |
| `PlayBeepForSeconds(pitch, seconds)` | `sound.beep` with `duration` |
| `StartPlayingBeep(freq)` | `sound.beep` with no duration (verify spec — may need `duration:0` or `duration:Infinity` convention) |
| `StopAllSounds()` | `sound.stop` |
| `SetVolume(level)` | `sound.set_volume` — bridge adds `volume` feature on speaker port |
| `PlayBuiltin(name)` / `StartSound(name)` | `sound.play` with `sound` field; dropdown populated from `builtin_sounds` capability array |
| `PlaySoundUntilDone(name)` | Send `sound.play`, block on completion event from bridge (verify bridge emits `{"event":"sound_complete"}` or similar) |

**Open question:** SPIKE FW 3.x `hub.sound` API surface — needs verification of which built-in sounds exist and whether `play_sound` supports a wait-for-completion mode.

### 3.6 `LegoSpikeMusic` *(new component, conditional)*

Component exists ONLY if SPIKE FW 3.x supports MIDI-style note playback. If not, music blocks fall back to looped `sound.beep` with frequency-from-note-name conversion client-side.

| Block | SSP v0.6 mapping |
|---|---|
| `PlayNote(note, duration)` | If MIDI supported: `sound.play` with `notes:` field; else client-side beep |
| `PlayDrum(name)` | `sound.play` with built-in percussion sound names |
| `Rest(duration)` | Client-side delay |
| `SetInstrument(name)` | Client-side state affecting subsequent `PlayNote` rendering (if MIDI: include in `notes:` payload; else affects beep waveform — likely no-op for SPIKE) |
| `SetTempo(bpm)` / `ChangeTempo(delta)` / `GetTempo()` | Client-side state, applied as `tempo:` field in `sound.play` |

**Open question:** if SPIKE doesn't natively support MIDI, decide whether to ship `LegoSpikeMusic` at all. Falling back to beep-based music gives poor results — may be better to omit and let users compose via individual `Beep` calls.

### 3.7 `LegoSpikeSystem` *(new component)*

| Block | SSP v0.6 mapping |
|---|---|
| `GetBatteryLevel()` → `BatteryLevelRead(percent)` event | `system.subscribe metric=battery`, cached value returned |
| `GetTemperature()` | `system.subscribe metric=temperature`, cached |
| `IsCharging()` | `system.subscribe metric=charging`, cached |
| `GetRSSI()` | `system.subscribe metric=connection_rssi`, cached |
| `WhenButtonPressed(button)` / `WhenButtonReleased(button)` / `WhenButtonHeld(button)` | `system.subscribe metric=button.<name>`; event fires on state-transition |
| `WhenHubButtonPressed` | Convenience — equivalent to `WhenButtonPressed("center")` |

### 3.8 Architecture update

After Phase 3 lands:
- **7 components default**: Connectivity, Motors, Movement, Light, Sensors, Sound, System
- **8 components if Music ships**: above + Music
- **Update memory files**: `architecture_multicomponent.md`, `mvp_status_and_postmvp.md`
- **Update `README.md`** Components table (currently lists 5)
- **Update `docs/SSP_BRIDGE_GUIDE.md`** with the full v0.6 mapping table

### 3.9 v0.7 wishlist candidates surfaced during Phase 3

To file as `SSP v0.7 wishlist` issue against `solaria-hub` once Phase 3 implementation surfaces concrete need:

- `motor.set_acceleration` action + `acceleration` feature on motor port
- `motor.run mode:"power"` to distinguish power-control from speed-control
- `orientation.reset` (or `imu.reset_yaw`) command for IMU
- `orientation.set_reference` for hub mounting orientation
- 3×3 RGB matrix display support (already partly covered by `display` port `depth:"rgb"` — but `width:3, height:3` would need spec example)
- Sensor-attached LEDs (distance sensor 4-LED indicator) — likely a `display` port type on sensor ports
- Movement `left_speed`/`right_speed` for tank-style control
- Sound `play_until_done` semantics — does `sound.play` return immediately or block? Spec should say.

---

## Phase 4 — Client/bridge architectural split (PR 2)

**Goal:** the Java extension stops being SPIKE-Prime-specific. Future Boost / EV3 / Arduino bridges work without changing the App Inventor side.

**Effort:** ~1–2 weeks. Most work is repo extraction and CI for the new bridge release artifact.

**Acceptance criteria:**
- `TransportProfile` interface exists; `SpikeTransportProfile` implements it
- Bridge Python lives in `solaria-bridge-spike-prime` repo with versioned releases
- This repo downloads the bridge program from a release artifact on first connection (decision #3)
- Existing users see no behaviour change — bridge cached locally after first download
- Falls back to baked-in default bridge if network unavailable on first run

### 4.1 Transport profile abstraction

- **4.1.1** Create `TransportProfile.java` interface:
  - `connect(BluetoothDevice device)`, `disconnect()`
  - `send(byte[] payload)`, `setOnReceive(Consumer<byte[]> handler)`
  - `discoveryFilter()` — what to look for in BLE scans / device discovery
  - `profileMetadata()` — returns the v0.6 §2.1 profile JSON
- **4.1.2** `SpikeTransportProfile` implementation — extract FD02 UUIDs, COBS+XOR framing, TunnelMessage 0x32 wrapping from `BluetoothInterfaceImpl` into one class
- **4.1.3** `SSPClient` takes a `TransportProfile`; everything above it becomes hardware-agnostic
- **4.1.4** Add `TransportProfileRegistry` (static) so future profiles register themselves

### 4.2 Bridge program extraction

- **4.2.1** New repo: `solaria-bridge-spike-prime` (matches v0.6 §10 reference implementations table)
- **4.2.2** Move hub-side Python out of the Java string constant into a versioned file in that repo
- **4.2.3** Set up GitHub Releases on that repo with the bridge program as a release artifact
- **4.2.4** This repo: replace embedded Python string with a `BridgeDownloader` class that fetches the appropriate version from the bridge repo's Releases API on first connection
- **4.2.5** Cache downloaded bridge locally in Android app's private storage; re-download only when version mismatch detected
- **4.2.6** Bake a known-good default bridge into the .aix as a fallback when network is unavailable on first run

### 4.3 Repo naming and discoverability

- Keep this repo as `appinventor-lego-spike-prime-extension` — App Inventor users find it by hardware name
- Defer `LegoSpike*` → `Solaria*` block rename indefinitely — discoverability beats internal naming consistency
- Internal abstraction (`SSPClient`, `TransportProfile`) is sufficient genericness for Phase 5 to plug in new bridges

### 4.4 Risks

- App Inventor dynamic dropdowns from live capability data may need designer-side work (`@Options` enums are compile-time; dropdowns populated from runtime capability data requires `@DesignerProperty` arrays with editor-time defaults plus runtime override)
- Repo split is a major version bump; existing `.aix` keeps working but new releases need version-matched bridge program
- Network dependency on first connection — graceful fallback to baked-in default handles offline case; need to test airplane-mode behaviour
- Bridge version skew — client speaks v0.6, bridge speaks v0.5 (or vice versa): client must read `ssp_version` in capability and degrade gracefully

---

## Phase 5 — Multi-hub support

**Goal:** Support LEGO Boost, SPIKE Essential, Technic Hub, EV3, and non-LEGO hardware via additional transport profiles + bridges. Single App Inventor extension drives all of them.

**No fixed timeline.** Each bridge is its own project; this phase tracks them.

### 5.1 LEGO Wireless Protocol (LWP) bridge

Covers Boost, SPIKE Essential, Technic Hub, two-port Hub (all use the same protocol).

- **5.1.1** New transport profile `lego-wireless-protocol`:
  - UUID: `00001623-1212-efde-1623-785feabcd123`
  - Different framing — message-prefix length byte, not COBS
- **5.1.2** New bridge repo: `solaria-bridge-lego-wireless`
  - Hub-side code is in firmware (LWP is built into LEGO's hubs); bridge implements the SSP↔LWP translation client-side rather than on-hub
  - This is a structural difference from SPIKE Prime — LWP bridges don't need program upload
- **5.1.3** Reuses this Java extension as-is once Phase 4 abstracts transport

### 5.2 EV3 bridge

EV3 uses classic Bluetooth SPP (RFCOMM), not BLE. v0.6 §2.1 EV3 profile example is the spec'd profile.

- **5.2.1** New bridge repo: `solaria-bridge-ev3`
- **5.2.2** RFCOMM transport implementation in `TransportProfileRegistry`:
  - Android `BluetoothSocket` with `BluetoothDevice.createRfcommSocketToServiceRecord(UUID)`
  - SDP UUID `00001101-0000-1000-8000-00805F9B34FB` (Serial Port Profile)
- **5.2.3** EV3 firmware integration:
  - EV3 has a Linux-based OS; bridge program runs as a daemon on the brick
  - SSP commands translate to EV3 system calls / lms2012 byte codes
- **5.2.4** Discovery uses Bluetooth Classic name pattern (`EV3*`) since RFCOMM has no equivalent of BLE advertising

### 5.3 Generic bridges

Tracked but not scoped here:

- `solaria-bridge-arduino` — USB Serial or BLE depending on board
- `solaria-bridge-microbit` — BLE
- `solaria-bridge-sphero` — BLE
- `solaria-bridge-vex` — possibly USB Serial
- Each as its own repo; single App Inventor extension drives all

### 5.4 Acceptance criteria for "Phase 5 done"

- At least 2 non-SPIKE bridges shipped (target: LWP + EV3, since both are LEGO and serve the same education market)
- Same App Inventor extension drives all 3 bridge types without rebuild
- App Inventor user can switch hubs by changing only the `LegoSpikeConnectivity` (or renamed) target

---

## Cross-cutting concerns

### Documentation maintenance

| File | Updated in | Notes |
|---|---|---|
| `ARCHITECTURE.md` | Phase 2 | Rule 5 obsolete after SSP; rewrite to reference SSP wire format |
| `CLAUDE.md` | Phase 2 | Critical Architectural Rules updated for SSP v0.6 |
| `README.md` | Phase 3 | Components table from 5 → 7 (or 8 with Music) |
| `docs/SSP_BRIDGE_GUIDE.md` | Phase 2 | New file — full v0.6 mapping table |
| `docs/IMPLEMENTATION_PLAN.md` | Each phase end | This file |
| Memory `architecture_multicomponent.md` | Phase 3 | 5 → 7 components |
| Memory `mvp_status_and_postmvp.md` | Phase 3 | "Post-MVP" items moved to completed |
| Memory `hub_command_protocol.md` | Phase 2 | Custom binary protocol → SSP v0.6 JSON |
| Memory `protocol_facts.md` | Phase 2 | Add SSP framing facts |

### Testing strategy (carried from CLAUDE.md original Phase 3)

- Unit tests on every new Java class — JUnit, run on Ant build
- Python-side unit tests on desktop MicroPython runtime
- End-to-end integration on physical hub — manual, checklists per phase
- Stress / longevity testing — 30-minute classroom-style session before each phase merges
- App Inventor block-level testing — manual; reference `.aia` project per component on Android device
- Multi-hub stress (Phase 2+): 4+ hubs on the same Android device, simultaneous connection-stability test (validates RSSI staleness logic from CLAUDE.md Rule 2 still works under SSP)
- Reconnection-after-disconnect: explicit test that capability re-declaration works on re-connect

### SSP spec contributions tracker

| Wishlist | Issue | Status | Items integrated |
|---|---|---|---|
| v0.2 | #1 | ✅ Integrated | Transport profiles, binary encoding (reserved), movement category, capability schema, request_id, heartbeat, sensor flow-control |
| v0.3 | #2 | ✅ Integrated | led.matrix category, display port, orientation port, sound.play payloads, speaker port, system.subscribe |
| v0.4 | #3 | ✅ Integrated | Parameter constraints, gesture event consistency |
| v0.5 | #4 | ✅ Integrated | Button format, array constraint type, gesture constraints, display dimension implicit constraints, plus `string` constraint type as bonus |
| v0.6 | #5 | ✅ Integrated | RFCOMM transport, binary encoding finalised, batch commands, motor duration/stop_action, sound.set_volume, led.matrix.brightness/orientation |
| v0.7 | TBD | 📝 Pending | Items in §3.9 above to file once Phase 3 surfaces concrete need |

---

## Inheritance from prior plans

For traceability — every item from prior planning documents is accounted for in this plan.

### From original CLAUDE.md (Known Issues / Next Steps section)

| Original phase | Item | Where in this plan |
|---|---|---|
| Phase 1 — Protocol Correction | Verify COBSEncoder constants | ✅ Phase 1 (done) |
| Phase 1 | Implement file upload protocol | ✅ Phase 1 (done) |
| Phase 1 | Implement program start | ✅ Phase 1 (done) |
| Phase 1 | Implement TunnelMessage send/receive | ✅ Phase 1 (done) |
| Phase 1 | Create hub-side Python controller | ✅ Phase 1 (done); rewritten in Phase 2.1 for SSP |
| Phase 2 — Hub-Side Python | Motor control all 6 ports | ✅ Phase 1 (done); refined in Phase 2 + Phase 3 |
| Phase 2 | LED matrix control | ✅ Phase 1 (basic); expanded in Phase 3.3 |
| Phase 2 | Sensor reading | ✅ Phase 1 (basic); expanded in Phase 3.4 |
| Phase 2 | Hub status (battery, orientation) | ⏳ Phase 3.7 (System component) + Phase 3.4 (IMU) |
| Phase 3 — Testing | BLE connection | ✅ Phase 1 (done); re-tested in Phase 2.5.3 |
| Phase 3 | Program upload reliability | ✅ Phase 1 (done); re-tested in Phase 2.5.3 |
| Phase 3 | TunnelMessage latency | ✅ Phase 1 (done); now JSON-payload benchmark in Phase 2.5.5 |
| Phase 3 | Reconnection after disconnect | ✅ Phase 1 (done); re-tested in Phase 2.5.3 |
| Future — Multi-Hub | Boost / EV3 / Essential | ⏳ Phase 5 |
| Future | Abstract BluetoothInterfaceImpl | ⏳ Phase 4 |

### From mvp_status_and_postmvp.md (post-MVP block list)

Every block listed in that memory is mapped to a Phase-3 section in §3.1–§3.7 above. The original memory groups blocks by component; this plan groups them the same way and adds the v0.6 SSP mapping for each.

### From the PR 1 + PR 2 plans drafted in this session

| Original PR | Now in this plan |
|---|---|
| PR 1 — SSP-compatible hub-side Python + Java JSON emit | Phase 2 |
| PR 2 — full client/bridge separation + SSP proposals | Phase 4 (with Phase 5 as the payoff) |

---

## Open questions (active)

These remain unresolved and should be answered before / during the relevant phase:

1. **SPIKE Prime FW 3.x sound API surface** — Which built-in sounds exist? Does `play_sound` block until complete or return immediately? (affects §3.5 `builtin_sounds` and `PlaySoundUntilDone`)
2. **SPIKE Prime FW 3.x MIDI support** — Does `hub.sound` accept note sequences or just frequency beeps? (affects §3.6 `LegoSpikeMusic` viability)
3. **Minimum reliable subscription interval over SPIKE BLE** — Memory says ~50 ms; needs benchmark with actual sensor load (affects §2.1 constraint declaration)
4. **`motor.run` indefinite-duration encoding** — Spec says `duration` is optional; does omitting it mean "indefinite"? Or should there be `duration: 0` / `duration: -1` convention? (affects §2.1.2 and §3.5)
5. **`motor.goto` relative vs absolute** — v0.6 has `position` constraint with `wraps:true` suggesting absolute. SPIKE has both relative and absolute Python APIs. Does v0.6 cover relative? Or need `x_relative_goto`? (affects §3.1 `GoToRelativeMotorPosition`)
6. **`movement.drive` left/right speeds** — v0.6 example shows `speed` + `steering`. Some hubs prefer tank-style explicit left/right. File v0.7 wishlist? (affects §3.2 `StartMovingAtSpeed`)
7. **SPIKE Prime 5×5 matrix max simultaneous-update rate** — for §3.3 light matrix animation benchmark
8. **`@Options` enum vs capability-driven dropdowns** — Phase 4 may need real designer-side investigation of App Inventor extension UI capabilities

---

## Instructions for whoever is implementing this

1. Read this document and `ARCHITECTURE.md` before starting any task.
2. Within a phase, tasks must be completed in section order. Within §2.3 (component migration), Connectivity must land first — other components depend on `CapabilityStore` integration.
3. Do not mark a phase complete until all acceptance criteria are verified on a physical hub.
4. Open the v0.7 wishlist issue against `solaria-hub` (per §3.9) when Phase 3 surfaces concrete needs — don't file speculatively.
5. Before committing, check that no MIT Hong Kong Innovation Node references have crept back in (the repo is public).
6. Commit messages: plain, no Co-Authored-By trailer (project owner preference).
7. Do not push to remote without explicit per-commit approval from the project owner.
8. Phase 2 perf gate (§2.5.5) is a hard merge blocker — if JSON drops payloads, switch to binary encoding (v0.6 §3.2) before merging.
