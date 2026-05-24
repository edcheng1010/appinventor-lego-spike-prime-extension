# Implementation Plan — LEGO SPIKE Prime App Inventor Extension

**Last revised:** 2026-05-24
**Status:** Phase 1 complete · Phase 2 next
**Author:** Edward Cheng

---

## Overview

This document is the working roadmap for the extension. It consolidates the original phase plan with the SSP migration work (PR 1 + PR 2) and the post-MVP block expansion. Five phases, in order.

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation — BLE, COBS, TunnelMessage, custom binary protocol, 5 working components | ✅ Complete |
| 2 | SSP v0.4 migration — same blocks, SSP wire format | ⏳ Next (PR 1) |
| 3 | Post-MVP block expansion — Sound, System, IMU, more motor/movement/light blocks | After Phase 2 |
| 4 | Client/bridge architectural split — TransportProfile, bridge extraction | PR 2 |
| 5 | Multi-hub support — Boost, EV3, SPIKE Essential, Arduino, etc. | Long-term |

---

## Decisions made (2026-05-24)

These resolve the open questions raised while planning. Settled — do not re-litigate without explicit cause.

1. **Validation strategy:** Client-side validation fires `OnError` event and refuses to send. Out-of-range parameters surface immediately to the App Inventor user rather than silently clamping or waiting for a bridge round-trip.
2. **JSON parse performance:** Commit to JSON for Phase 2. Add a perf benchmark gate before merging — sustained 20 Hz movement updates for 60 s with no payload drops. If perf fails, file an SSP v0.6 wishlist item for binary encoding finalisation; mitigate temporarily by throttling movement updates to 10 Hz. Do not pre-optimise.
3. **Bridge program embedding:** End state is download-on-connect (versioned bridge releases, decoupled from the .aix). Implement during Phase 4. Phase 2 keeps the bridge program embedded as a Java string — minimum change while migrating wire format.
4. **Phase ordering:** Phase 2 before Phase 3. Doing post-MVP block expansion *after* SSP migration avoids duplicating new commands in two wire formats.

---

## SSP v0.5 / v0.6 wishlist dependency analysis

**No hard dependencies on v0.5 or v0.6 for any of Phases 2, 3, or 4.**

| Wishlist item | Phase impact | Workaround if not in spec |
|---|---|---|
| v0.5 — button event format ambiguity | None | Bridge picks the `event:"system",metric:"button.<name>"` form (already settled in v0.3 §6.5); the v0.4 §3.1.2 `event:"button"` example is treated as historical |
| v0.5 — array constraint type | None | SPIKE's status LED uses enum (named colors), not RGB; no array-shaped params on this bridge |
| v0.5 — gesture constraints | Phase 3 polish | Hardcode the SPIKE gesture vocabulary in `LegoSpikeSensors.WhenGesture` dropdown until spec'd |
| v0.5 — display dimensions vs coordinate constraints | None | Document `x`/`y` ranges in the bridge README; declare `width`/`height` at port level only |
| v0.6 — binary encoding finalisation | Phase 2 fallback only (decision #2) | Throttle to 10 Hz if JSON perf is bad |
| v0.6 — batch commands | Phase 3 polish (e.g., setting 25 light matrix pixels at once) | Loop of single `led.matrix.pixel` calls; acceptable for 5×5 grid |
| v0.6 — RFCOMM transport | Phase 5 only (EV3 needs it) | Not relevant until Phase 5 |
| v0.6 — DFU, stream multiplexing, auth | None for this bridge | n/a |

**Conclusion:** proceed with Phases 2–4 against SSP v0.4 as it stands today. The v0.5 wishlist items would polish edge cases but won't change the implementation shape.

---

## Phase 1 — Foundation *(COMPLETE ✓)*

Originally "Protocol Correction" in the pre-MVP plan. Delivered the working MVP that's currently on `main`.

**Shipped:**
- BLE scan with RSSI staleness ghost-filtering
- COBS encoding with verified constants (delimiter 0x02, XOR 0x03)
- File upload protocol (`ClearSlot` → `StartFileUpload` → `TransferChunk` → `ProgramFlow`)
- TunnelMessage send/receive (opcode 0x32)
- Hub-side Python program embedded in Java as a string constant
- 5 components: `LegoSpikeConnectivity`, `LegoSpikeMotors`, `LegoSpikeMovement`, `LegoSpikeLight`, `LegoSpikeSensors`
- Custom `MTR`/`MOV`/`LGT`/`SEN` binary command protocol
- Tested on physical SPIKE Prime 3.x hubs in classroom conditions

**Known limitations** (carry into Phase 2 planning):
- Center button LED is owned by firmware during BLE/TunnelMessage mode — cannot be re-coloured live (block removed)
- `hub.motion_sensor.tilt_angles()` may return 0 on some firmware revisions (IMU fallback in place)
- `BluetoothLE.BytesReceived → LegoSpikeConnectivity.OnBytesReceived` must be wired manually in App Inventor blocks (auto-wiring fails on the BluetoothLE extension version we depend on)

---

## Phase 2 — SSP v0.4 migration (PR 1)

**Goal:** swap the wire protocol from custom binary to SSP v0.4. The App Inventor block API surface stays identical from the user's perspective. Internal protocol becomes hardware-agnostic.

**Effort:** ~2 weeks one developer. Dominated by the hub-side Python rewrite and end-to-end physical-hub testing.

**Acceptance criteria:**
- Every existing block produces equivalent behaviour on a physical hub
- Bridge emits capability declaration on connect, with constraints per v0.4 §5.2
- Sustained 20 Hz movement updates work (or mitigation per decision #2)
- New `OnError` event fires for out-of-range parameters before any bytes go on wire
- Heartbeat (5 s ping, 10 s disconnect on missed pongs) works end-to-end
- No regression in connection stability vs current MVP

### 2.1 Hub-side Python rewrite

Tasks:
- **2.1.1** Replace `MTR`/`MOV`/`LGT`/`SEN` binary parser with `json.loads()` on newline-delimited frames
- **2.1.2** Implement command dispatcher for SPIKE-relevant SSP commands:
  - `motor.run`, `motor.stop`, `motor.goto`, `motor.reset`
  - `movement.configure`, `movement.drive`, `movement.turn`, `movement.stop`
  - `led.set` (status LED), `led.matrix.pixel`, `led.matrix.image`, `led.matrix.text`, `led.matrix.clear`
  - `sound.beep`, `sound.play` (built-in only — verify SPIKE FW 3.x sound name list)
  - `sensor.subscribe`, `sensor.unsubscribe`, `sensor.read`
  - `system.ping`, `system.info`, `system.subscribe`, `system.read`, `system.unsubscribe`
- **2.1.3** Capability declaration builder — emit on tunnel-ready:
  - Enumerate connected motor ports (A–F) by inspecting `hub.port.<id>` for `Motor` instances
  - Enumerate sensor ports (color, distance, force) by device type
  - Always declare `display`, `status`, `imu`, `speaker` virtual ports
  - Always declare `system_metrics`: battery, charging, temperature, button.left/right/center, connection_rssi
  - Include canonical feature constraints per v0.4 §5.2 (table below)
- **2.1.4** `SubscriptionManager` class — single async loop, emits `{"event":"sensor",...}` per registered subscription, honors `mode`/`interval`/`min_change`
- **2.1.5** Heartbeat handler — respond to `system.ping` with `pong` event; disconnect after 10 s without ping (only when subscriptions active)
- **2.1.6** Gesture event emit format (v0.4 sensor-form):
  ```json
  {"event":"sensor","port":"imu","type":"gesture","value":"shake"}
  ```
- **2.1.7** Structured error events with `request_id` echo when present

**Constraints to declare** (SPIKE Prime 3.x, per v0.4 §5.2):

| Port | Feature | Constraint |
|---|---|---|
| A–F motor | `speed` | `{"type":"int","min":-100,"max":100}` |
| A–F motor | `position` | `{"type":"int","min":0,"max":359,"wraps":true}` |
| `status` led | `color` | `{"type":"enum","values":["red","orange","yellow","green","cyan","blue","violet","magenta","white","off"]}` |
| `display` | `brightness` (matrix pixel) | `{"type":"int","min":0,"max":100}` |
| `imu` orientation | `pitch`/`roll` | `{"type":"int","min":-180,"max":180}` |
| `imu` orientation | `yaw` | `{"type":"int","min":0,"max":360,"wraps":true}` |
| sensor subscribe | `interval` | `{"type":"int","min":50}` |

### 2.2 Java side new infrastructure

Tasks:
- **2.2.1** `SSPMessage.java` — `JSONObject`-based command builder; `withRequestId(id)`, `withPort(port)`, `withParam(key, value)`; serialises to `bytes + \n`
- **2.2.2** `SSPParser.java` — parses incoming newline-delimited JSON frames; dispatches to typed listeners (`onCapability`, `onSensor`, `onSystem`, `onError`, `onPong`)
- **2.2.3** `SSPClient.java` — owns the COBS+TunnelMessage transport via existing `BluetoothInterfaceImpl`; exposes `send(SSPMessage)`, `setListener(SSPListener)`, manages `request_id` counter
- **2.2.4** `SpikeTransportProfile.java` — static profile constant matching v0.4 §2.1 example exactly
- **2.2.5** `CapabilityStore.java` — caches parsed v0.4 capability data; queryable by other components (`getDeviceType()`, `getPortType(portId)`, `getConstraint(portId, feature, attribute)`, `hasFeature(portId, feature)`)
- **2.2.6** `Validator.java` — consults `CapabilityStore` to validate `SSPMessage` before send; throws `ValidationException` for out-of-range values; called by `SSPClient.send()`
- **2.2.7** `HeartbeatManager.java` — 5 s ping timer, lifecycle-managed (started on first subscribe, stopped on last unsubscribe); fires `OnHeartbeatLost` after 2 missed pongs
- **2.2.8** Client-side `SubscriptionManager` — tracks active subscriptions per port, caches last sensor value for one-shot getters

### 2.3 Component migration

Tasks (one commit per component, in this order):
- **2.3.1** `LegoSpikeConnectivity` — gains:
  - `OnCapabilityReceived` event
  - `GetDeviceType()`, `GetAvailablePorts()`, `GetSupportedEncodings()` blocks
  - `OnHeartbeatLost` event
  - `OnError(code, message, requestId)` event (new — used by Validator)
  - Existing `OnBytesReceivedFromHub` rewired through `SSPParser`
- **2.3.2** `LegoSpikeMotors` — all methods now build `SSPMessage` via `SSPClient` instead of binary payloads
- **2.3.3** `LegoSpikeMovement` — same; 1-to-1 with SSP `movement.*` (no semantic change)
- **2.3.4** `LegoSpikeLight` — `status` LED → `led.set`; light matrix → `led.matrix.*`
- **2.3.5** `LegoSpikeSensors` — switch from on-demand `read` getters to background subscription model:
  - Getter blocks (`GetColor()`, `GetDistance()`, etc.) auto-subscribe on first call (mode: `interval`, default 100 ms)
  - Return last cached value; explicit `Refresh()` block forces a one-shot `sensor.read`
  - New optional params on subscription blocks: `Mode` (interval/on_change/hybrid), `MinChange`

### 2.4 Cleanup

- **2.4.1** Delete `MessageBuilder.java` (binary serialiser obsolete)
- **2.4.2** Delete custom `MTR`/`MOV`/`LGT`/`SEN` command constants
- **2.4.3** Update embedded Python program string
- **2.4.4** Update `ARCHITECTURE.md` — Rule 5 ("never send direct motor/LED commands via BLE") becomes obsolete; replace with SSP wire-format rules
- **2.4.5** Add `docs/SSP_BRIDGE_GUIDE.md` documenting how this bridge maps to SSP v0.4

### 2.5 Testing

- **2.5.1** Unit tests: `SSPMessage`, `SSPParser`, `SSPClient`, `Validator`, `CapabilityStore`
- **2.5.2** Python-side tests on a desktop MicroPython runtime — exercise the command dispatcher and subscription manager
- **2.5.3** End-to-end integration on physical hub:
  - Each existing block produces the same behaviour as the MVP
  - Capability declaration matches connected ports when hot-swapping sensors
  - Heartbeat survives an Android screen-lock cycle
  - 20 Hz movement update perf benchmark (decision #2 gate)
- **2.5.4** Connection stability stress: 30-minute classroom-style session with disconnect/reconnect cycles

### 2.6 Risks

- **JSON parse cost on the hub.** Mitigation per decision #2 (benchmark gate, throttle fallback).
- **Subscription model changes sensor block semantics.** "Get current value" becomes "get last cached value". Mitigated by auto-subscribe-on-first-read; document in block tooltips.
- **Capability declaration arrival timing.** ~1 s delay between tunnel-open and capability emit. Connectivity blocks need to gate on `OnCapabilityReceived` rather than `HubConnected`.
- **Validator UX.** Firing `OnError` for every out-of-range value could be noisy in classroom settings (students typing 200 into a speed field). Consider rate-limiting `OnError` to one event per (port, parameter) per second.

---

## Phase 3 — Post-MVP block expansion

**Goal:** add the blocks documented in the post-MVP roadmap, built on SSP from day one. No new wire-protocol work — Phase 2 made everything possible by adding SSP commands.

**Effort:** ~2–3 weeks, paced by App Inventor designer work and end-user testing.

**Acceptance criteria:**
- All blocks from sections 3.1–3.7 below available in App Inventor palette
- 7-component architecture (5 existing + Sound + System)
- `architecture_multicomponent.md` memory updated to reflect 7 components

### 3.1 LegoSpikeMotors expansion

- `RunMotorForDuration(direction, amount, unit)` — `motor.run` + client-side duration timer
- `MotorGoToPosition(position, direction)` — `motor.goto`
- `GetMotorPosition()` / `MotorPositionRead` event — `sensor.subscribe` on motor port (`position` feature)
- `GetMotorSpeed()` / `MotorSpeedRead` event — same, `speed` feature
- `GoToRelativeMotorPosition`, `ResetRelativeMotorPosition`, `RelativeMotorPosition`
- `StartMotorWithPower(power)` — investigate v0.4 spec coverage; may need `motor.power` extension
- `StopAndCoastMotor(port)` — `motor.stop` with `brake: false` (verify spec)
- `SetMotorAcceleration` — needs spec coverage check

### 3.2 LegoSpikeMovement expansion

- `MoveForDuration(direction, amount, unit)` — `movement.drive` + client-side duration
- `MoveWithSteeringForDuration(steering, amount, unit)` — same
- `StartMovingAtSpeed(leftSpeed, rightSpeed)` — tank-style; verify v0.4 covers (`movement.drive` with explicit left/right)
- `SetMotorRotationDistance`, `SetMovementBrakeAtStop`, `SetMovementAcceleration` — investigate spec coverage

### 3.3 LegoSpikeLight expansion (display port)

- `SetPixel(x, y, brightness)` — `led.matrix.pixel`
- `ShowImage(name)` — `led.matrix.image`
- `ShowText(text, scroll)` — `led.matrix.text`
- `ClearMatrix()` — `led.matrix.clear`
- `TurnOnLightMatrixForSeconds(image, seconds)` — image then clear after delay
- `SetLightMatrixBrightness`, `RotateLightMatrix`, `SetLightMatrixOrientation` — investigate spec coverage; likely v0.5+ additions
- `LightUpDistanceSensor(topLeft, topRight, bottomLeft, bottomRight)` — sensor-attached LEDs; ship as `x_distance_led_*` extension until spec'd

### 3.4 LegoSpikeSensors expansion (IMU + threshold events)

- `GetHubAcceleration` → `sensor.read` on `imu` port (`acceleration` feature)
- `GetHubAngularVelocity` → same, needs verify
- `GetHubOrientation` → wraps pitch/roll/yaw
- `WhenGesture(type)` — gesture events on `imu` port; dropdown values hardcoded until v0.5 lands
- `WhenHubShaken` — convenience over `WhenGesture("shake")`
- `ResetYaw`, `SetHubSensorOrientation` — IMU commands; verify SPIKE FW 3.x API
- Threshold events: `WhenColorIs`, `WhenCloserThan`, `WhenPressureIs`, `WhenTilted`, `WhenTimer` — built on `sensor.subscribe mode:"on_change"` with thresholds evaluated client-side

### 3.5 LegoSpikeSound *(new component)*

- `Beep(freq, duration)` — `sound.beep`
- `StartPlayingBeep(freq)`, `StopAllSounds()` — `sound.beep` + `sound.stop`
- `SetVolume(level)` — verify spec coverage
- `PlayBuiltin(name)` — `sound.play` with `sound` field; dropdown populated from `builtin_sounds` capability
- `PlaySoundUntilDone(name)` — wait for completion event from bridge

### 3.6 LegoSpikeMusic *(new component, may merge with Sound)*

Verify SPIKE FW 3.x actually supports MIDI before committing this component. If not, scope to:
- `PlayNote(note, duration)` — wraps `sound.beep` with frequency-from-note-name conversion client-side
- `PlayDrum(name)` — wraps `sound.play` with built-in percussion sound names
- `Rest`, `SetTempo`, `ChangeTempo`, `GetTempo` — client-side tempo tracking on top of beep loop

If SPIKE does support MIDI (via `sound.play notes:` field), expose the full music block surface from the post-MVP roadmap.

### 3.7 LegoSpikeSystem *(new component)*

- `GetBatteryLevel()` → `system.subscribe metric=battery`, cached
- `GetTemperature()` → `system.subscribe metric=temperature`, cached
- `IsCharging()` → `system.subscribe metric=charging`, cached
- `GetRSSI()` → `system.subscribe metric=connection_rssi`, cached
- `WhenButtonPressed(button)`, `WhenButtonReleased(button)`, `WhenButtonHeld(button)` → `system.subscribe metric=button.<name>`

### 3.8 Architecture update

After Phase 3 lands:
- 7 components: Connectivity, Motors, Movement, Light, Sensors, Sound, System
- Possibly 8 if Music splits from Sound (depends on §3.6 verification)
- Update `architecture_multicomponent.md` memory file
- Update `README.md` Components table

---

## Phase 4 — Client/bridge architectural split (PR 2)

**Goal:** the Java extension stops being SPIKE-Prime-specific. Future Boost / EV3 / Arduino bridges work without changing the App Inventor side.

**Effort:** ~1–2 weeks. Most of the work is repo extraction and CI for the new bridge release artifact.

**Acceptance criteria:**
- `TransportProfile` interface exists; `SpikeTransportProfile` implements it
- Bridge Python lives in `solaria-bridge-spike-prime` repo with versioned releases
- This repo downloads the bridge program from a release artifact on first connection
- Existing users see no behaviour change — bridge cached locally after first download

### 4.1 Transport profile abstraction

- `TransportProfile` interface: `connect()`, `disconnect()`, `send(byte[])`, `setOnReceive(Consumer<byte[]>)`, `discoveryFilter()`, `profileMetadata()` (returns the v0.4 §2.1 profile JSON)
- `SpikeTransportProfile` implementation — extracts FD02 UUIDs, COBS+XOR framing, TunnelMessage 0x32 wrapping into one class
- `SSPClient` takes a `TransportProfile`; everything above it becomes hardware-agnostic

### 4.2 Bridge program extraction

- New repo: `solaria-bridge-spike-prime`
- Move hub-side Python out of the Java string constant
- Versioned releases on that repo with GitHub Releases attached
- This repo: replace embedded Python with a downloader that fetches the appropriate version from the bridge releases on first connection
- Cache downloaded bridge locally; re-download only when version changes
- Fall back to a known-good baked-in version if network is unavailable on first run

### 4.3 Repo naming

- Keep this repo as `appinventor-lego-spike-prime-extension` — App Inventor users find it by hardware name
- Defer `LegoSpike*` → `Solaria*` block rename indefinitely — discoverability beats internal naming consistency

### 4.4 Risks

- App Inventor dynamic dropdowns from live capability data may need designer-side work (`@Options` enums are compile-time)
- Repo split is a major version bump; existing `.aix` keeps working but new releases need version-matched bridge
- Network dependency on first connection — needs graceful fallback to baked-in default

---

## Phase 5 — Multi-hub support

**Goal:** Support LEGO Boost, EV3, SPIKE Essential, and non-LEGO hardware via additional transport profiles + bridges. Single App Inventor extension drives all of them.

**No fixed timeline.** Each bridge is its own project; this phase tracks them.

### 5.1 LEGO Wireless Protocol bridge

- Covers Boost, SPIKE Essential, Technic Hub, two-port Hub
- New profile: `lego-wireless-protocol` (UUID `00001623-1212-efde-1623-785feabcd123`, message-prefix framing)
- New bridge repo: `solaria-bridge-lego-wireless`
- Reuses this Java extension as-is (now hardware-agnostic from Phase 4)

### 5.2 EV3 bridge

- EV3 uses classic Bluetooth SPP, not BLE
- Requires SSP v0.6 `transport: "rfcomm"` extension to transport profiles
- New bridge repo: `solaria-bridge-ev3`

### 5.3 Generic bridges

- `solaria-bridge-arduino` (USB Serial or BLE depending on board)
- `solaria-bridge-microbit` (BLE)
- `solaria-bridge-sphero` (BLE)
- Each as its own repo; single App Inventor extension drives all

---

## Cross-cutting concerns

### Documentation maintenance

- `ARCHITECTURE.md` — rewrite in Phase 2 (current Rule 5 about "never send direct motor/LED commands" is obsolete after SSP migration)
- `CLAUDE.md` — update Critical Architectural Rules in Phase 2; Rule 5 replaced with SSP wire-format rules
- `README.md` — Components table updated after Phase 3
- `docs/SSP_BRIDGE_GUIDE.md` — new file in Phase 2, documents how this bridge maps to SSP v0.4
- Memory files (`architecture_multicomponent.md`, `mvp_status_and_postmvp.md`, `hub_command_protocol.md`) — update at end of each phase

### SSP spec contributions tracker

| Wishlist | Status | Items |
|---|---|---|
| v0.2 | ✅ Integrated | Transport profiles, binary encoding (reserved), movement category, capability schema canonical features, feature→command mapping, request_id, heartbeat, sensor flow-control |
| v0.3 | ✅ Integrated | led.matrix category, display port, orientation port, sound.play payloads, speaker port, system.subscribe |
| v0.4 | ✅ Integrated | Parameter constraints, gesture event consistency |
| v0.5 | 🔄 Filed | Button event format, array constraint type, gesture constraints, display dims |
| v0.6 | 📝 Future | RFCOMM transport (Phase 5), binary encoding finalisation (Phase 2 fallback), batch commands (Phase 3 polish), DFU, multi-device, auth |

### Testing strategy

- Unit tests for every new Java class — JUnit, run on Ant build
- Python-side unit tests on desktop MicroPython runtime — exercise command dispatcher without needing a physical hub
- End-to-end integration on a physical hub — manual, gated by checklists per phase
- Stress / longevity testing — 30-minute classroom-style session before each phase merges
- App Inventor block-level testing — manual; build a reference `.aia` project per component, run on Android device

---

## Open questions (active)

1. Does SPIKE Prime FW 3.x's `hub.sound.play()` accept arbitrary built-in names, or is the list firmware-fixed? (affects §3.5 `builtin_sounds` capability enumeration)
2. Does SPIKE Prime FW 3.x support any MIDI-style playback, or is it beep-only? (affects whether `LegoSpikeMusic` is its own component in §3.6)
3. What is the actual minimum reliable subscription interval over SPIKE Prime BLE? Memory says ~50 ms, needs benchmarking. (affects §2.1.3 constraint declaration)
4. Should `LegoSpikeMovement.MoveForDuration` rely on the bridge-side `motor_pair.move_for_degrees()` or client-side timing? (affects §3.2; bridge-side is more accurate but requires `movement.move_for_duration` extension — not in v0.4)

---

## Instructions for whoever is implementing this

1. Read this document and `ARCHITECTURE.md` before starting any task.
2. Phase 2 tasks must be completed in section order (2.1 → 2.2 → 2.3 → 2.4 → 2.5). Within 2.3, components must migrate in order (Connectivity first — other components depend on its `CapabilityStore` integration).
3. Do not mark a phase complete until the acceptance criteria are all verified on a physical hub.
4. Before committing, check that no MIT Hong Kong Innovation Node references have crept back in (the repo is public now).
5. Commit messages: plain, no Co-Authored-By trailer (project owner preference).
6. Do not push to remote without explicit per-commit approval from the project owner.
