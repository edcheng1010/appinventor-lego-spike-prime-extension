> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../../NOTICE) for trademark and licensing details.*

# LEGO Official cobs.py — Line-by-Line Analysis

## Constants (AUTHORITATIVE)
```
DELIMITER = 0x02        # End-of-frame marker
NO_DELIMITER = 0xFF     # Code word meaning "no delimiter in this block"
COBS_CODE_OFFSET = 0x02 # Offset added to code word (equals DELIMITER)
MAX_BLOCK_SIZE = 84     # Max block size INCLUDING the code word
XOR = 3                 # XOR mask applied to all bytes
```

## Encode Algorithm
1. Start a new block: append NO_DELIMITER (0xFF) placeholder at code_index, set block=1
2. For each byte in data:
   - If byte > DELIMITER (i.e., byte > 0x02): append byte, block++
   - If byte <= DELIMITER OR block > MAX_BLOCK_SIZE:
     - If byte <= DELIMITER: update code word = (byte * MAX_BLOCK_SIZE) + (block + COBS_CODE_OFFSET)
     - Begin new block
3. Final code word: buffer[code_index] = block + COBS_CODE_OFFSET

## Decode Algorithm
1. Unescape function: given code word:
   - If code == 0xFF: return (None, MAX_BLOCK_SIZE + 1) — no delimiter
   - Otherwise: value, block = divmod(code - COBS_CODE_OFFSET, MAX_BLOCK_SIZE)
   - If block == 0: block = MAX_BLOCK_SIZE, value -= 1
   - Return (value, block)
2. Process first byte as code word
3. For remaining bytes: decrement block, if block > 0 append byte; else unescape next code word

## Pack (for transmission)
1. Encode data with COBS
2. XOR every byte with 0x03
3. Append DELIMITER (0x02)

## Unpack (received frame)
1. If first byte is 0x01, skip it (unused priority byte)
2. Strip trailing delimiter
3. XOR every byte with 0x03
4. COBS decode

## CRITICAL OBSERVATIONS:
- COBS_CODE_OFFSET = DELIMITER = 0x02 (NOT separate values)
- The delimiter value 0x02 is special — bytes with value 0x00, 0x01, 0x02 are ALL treated as "delimiter" values (byte <= DELIMITER)
- The XOR with 0x03 is applied AFTER COBS encoding, not during
- There's a priority byte (0x01) that may appear at the start of received frames — must be handled
- MAX_BLOCK_SIZE = 84 includes the code word itself
