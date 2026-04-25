# COBS Test Vectors (from LEGO official test_cobs.py)

These test vectors are AUTHORITATIVE. Our Java COBSEncoder MUST produce identical output.

## Test Case 1: Contains 0, 1, 2 to escape
**Input (raw):**
```
00 01 02 03 04 05 06 00 01 02 03 04 05 54 EA 36 00 2D 17 0C
```
**Expected pack() output:**
```
00 54 A8 04 00 07 06 05 54 A8 0A 00 07 06 57 E9 35 05 2E 14 0F 02
```

## Test Case 2: Contains nothing to escape
**Input (raw):**
```
FF FE DF D5 7D AF 64 61 36 15 41 2D
```
**Expected pack() output:**
```
0C FC FD DC D6 7E AC 67 62 35 16 42 2E 02
```

## Test Case 3: Complex case with multiple escape sequences
**Input (raw):** (170 bytes)
```
0A 03 00 3D 5B 97 00 B9 D9 57 70 C3 DD CF D8 28 3F DC FD 2A F8 55 C3 AF 06 7E B5 32 17 AE FA FF 03 B7 1E E0 0E 02 C7 56 39 E3 00 F2 EA FF C2 F3 6B A2 69 EB FB B1 4D 49 5D BB 7A 95 EB AB D5 07 5D B1 4F B3 2B F4 00 31 F3 0A 2E D3 12 62 6B 45 86 8A C4 13 86 60 5F 8C 36 95 BB 95 1B 46 D8 4F 75 05 7B ED F9 C4 CF A7 72 36 E7 A6 D5 CD CB 76 3D E0 76 59 6B 2C 0B 8D 44 6C 17 5B 19 12 47 2A 32 D4 97 4A 4C 88 96 98 1C 2D 91 BE AC E0 81 A3 52 A2 ED B5 47 6F 5C 9A B2 D0 00 65 6C 50 0B AD 21 5E 05 FD B7 C0 0E D7 16 DA 7F F5 29 75 6B 1F 75 2C
```
**Expected pack() output:** (180 bytes — see test_cobs.py for exact bytes)

## CRITICAL: These test vectors prove that:
1. Bytes 0x00, 0x01, and 0x02 are ALL treated as delimiter values (byte <= DELIMITER)
2. The XOR with 0x03 is applied AFTER COBS encoding
3. The delimiter 0x02 is appended at the end
4. The pack output always ends with 0x02
