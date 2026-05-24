> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../NOTICE) for trademark and licensing details.*

# Correct LEGO SPIKE Prime Bluetooth UUIDs

## Official SPIKE Prime Protocol UUIDs

Based on the official LEGO SPIKE Prime protocol documentation (https://lego.github.io/spike-prime-docs/), the **correct UUIDs for LEGO SPIKE Prime** are:

### SPIKE Prime BLE Service

| Item | UUID |
|------|------|
| **Service** | `0000FD02-0000-1000-8000-00805F9B34FB` |
| **RX (Receive)** | `0000FD02-0001-1000-8000-00805F9B34FB` |
| **TX (Transmit)** | `0000FD02-0002-1000-8000-00805F9B34FB` |

---

## Important Clarification

I apologize for the confusion in my previous responses. There are **two different LEGO Bluetooth protocols**:

### 1. LEGO Wireless Protocol 3.0 (General LEGO Devices)
Used by: LEGO Boost, LEGO Technic, and other general LEGO hubs
- Service: `00001623-1212-EFDE-1623-785FEABCD123`
- Characteristic: `00001624-1212-EFDE-1623-785FEABCD123`

### 2. SPIKE Prime Protocol (SPIKE Prime Specific)
Used by: LEGO SPIKE Prime Hub **ONLY**
- Service: `0000FD02-0000-1000-8000-00805F9B34FB`
- RX Characteristic: `0000FD02-0001-1000-8000-00805F9B34FB`
- TX Characteristic: `0000FD02-0002-1000-8000-00805F9B34FB`

---

## Device Authentication for SPIKE Prime

When implementing UUID authentication after connecting to a BLE device, verify the device has:

**For SPIKE Prime Hub:**
1. Check for Service UUID: `0000FD02-0000-1000-8000-00805F9B34FB`
2. Verify RX Characteristic: `0000FD02-0001-1000-8000-00805F9B34FB`
3. Verify TX Characteristic: `0000FD02-0002-1000-8000-00805F9B34FB`

If all three are present → Device is a genuine LEGO SPIKE Prime hub

**If SPIKE Prime UUIDs are NOT found:**
- Check for LEGO Wireless Protocol 3.0 UUIDs (fallback for other LEGO hubs)
- If neither set is found → Device is not a LEGO hub, disconnect

---

## Communication Flow for SPIKE Prime

1. **Connect** to the BLE device
2. **Verify** the device has the SPIKE Prime service UUIDs
3. **Send data** to the hub using write-without-response on the **RX characteristic**
4. **Receive data** from the hub as notifications on the **TX characteristic**
5. **Enable notifications** on the TX characteristic before sending any commands

---

## Handshake Protocol

Upon connecting to a SPIKE Prime hub, the client must:

1. Send an `InfoRequest` message to the hub
2. Receive an `InfoResponse` containing:
   - Maximum packet size
   - Maximum chunk size
   - Hub capabilities

These limits must be honored when sending subsequent messages.

---

## References

- **Official SPIKE Prime Protocol Documentation:** https://lego.github.io/spike-prime-docs/
- **SPIKE Prime Connection Setup:** https://lego.github.io/spike-prime-docs/connect.html
- **SPIKE Prime Python Examples:** https://github.com/LEGO/spike-prime-docs/examples/python

---

## Summary

**The correct SPIKE Prime UUIDs are:**
- Service: `0000FD02-0000-1000-8000-00805F9B34FB`
- RX: `0000FD02-0001-1000-8000-00805F9B34FB`
- TX: `0000FD02-0002-1000-8000-00805F9B34FB`

These are different from the general LEGO Wireless Protocol 3.0 UUIDs and are specific to SPIKE Prime hubs.

