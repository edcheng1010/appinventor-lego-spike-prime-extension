package io.github.appinventor.legospike;

/**
 * Consistent Overhead Byte Stuffing (COBS) encoder with XOR masking for LEGO SPIKE Prime communication
 * 
 * Enhanced to match the SPIKE™ Prime protocol requirements, which specifies that
 * 0x00, 0x01, and 0x02 bytes must be escaped and all bytes XORed with 0x03.
 */
public class COBSEncoder {
    // XOR mask value for SPIKE™ Prime protocol - 0x03 as specified in the documentation
    private static final byte XOR_MASK = (byte)0x03;
    
    /**
     * Encodes a byte array using COBS encoding with XOR masking according to SPIKE™ Prime protocol
     * 
     * @param data the data to encode
     * @param addDelimiter whether to add a delimiter byte at the end
     * @return the encoded data
     */
    public static byte[] encode(byte[] data, boolean addDelimiter) {
        if (data == null || data.length == 0) {
            return addDelimiter ? new byte[]{0} : new byte[0];
        }
        
        // In SPIKE™ Prime COBS encoding, we need to escape 0x00, 0x01, and 0x02
        // This increases the worst-case overhead
        byte[] encoded = new byte[data.length * 2 + 1 + (addDelimiter ? 1 : 0)]; // Worst case scenario
        
        int code_ptr = 0;
        int write_ptr = 1;
        int code = 1;
        
        for (int read_ptr = 0; read_ptr < data.length; read_ptr++) {
            byte b = data[read_ptr];
            
            // SPIKE™ Prime protocol requires escaping 0x00, 0x01, and 0x02
            if (b == 0x00 || b == 0x01 || b == 0x02) {
                encoded[code_ptr] = (byte) code;
                code_ptr = write_ptr++;
                code = 1;
                
                // For 0x01 and 0x02, we still write them to the output after escaping
                // but we apply XOR masking as required by the protocol
                if (b != 0x00) {
                    // Apply XOR mask to 0x01 and 0x02 bytes
                    encoded[write_ptr++] = (byte)(b ^ XOR_MASK);
                    code++;
                }
            } else {
                // Apply XOR mask to all other bytes as well
                encoded[write_ptr++] = (byte)(b ^ XOR_MASK);
                code++;
                if (code == 0xFF) {
                    encoded[code_ptr] = (byte) code;
                    code_ptr = write_ptr++;
                    code = 1;
                }
            }
        }
        
        encoded[code_ptr] = (byte) code;
        
        if (addDelimiter) {
            encoded[write_ptr] = 0;
            write_ptr++;
        }
        
        // If we didn't use all the allocated space, create a new array of the correct size
        if (write_ptr < encoded.length) {
            byte[] result = new byte[write_ptr];
            System.arraycopy(encoded, 0, result, 0, write_ptr);
            return result;
        }
        
        return encoded;
    }
    
    /**
     * Decodes a COBS-encoded byte array with XOR masking according to SPIKE™ Prime protocol
     * 
     * @param data the encoded data
     * @return the decoded data
     */
    public static byte[] decode(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        
        // Remove trailing zero if present (delimiter)
        int dataLength = data.length;
        if (data[dataLength - 1] == 0) {
            dataLength--;
        }
        
        if (dataLength == 0) {
            return new byte[0];
        }
        
        // Allocate maximum possible size for decoded data
        byte[] decoded = new byte[dataLength];
        
        int read_ptr = 0;
        int write_ptr = 0;
        
        while (read_ptr < dataLength) {
            int code = data[read_ptr++] & 0xFF;
            
            if (code == 0) {
                break; // End of data
            }
            
            for (int i = 1; i < code; i++) {
                if (read_ptr >= dataLength) {
                    break;
                }
                
                byte b = data[read_ptr++];
                
                // Apply XOR mask to recover the original byte
                // All bytes are XORed with 0x03 in the SPIKE™ Prime protocol
                b = (byte)(b ^ XOR_MASK);
                
                decoded[write_ptr++] = b;
            }
            
            // Add a zero byte if this isn't the end of the data
            if (read_ptr < dataLength && code < 0xFF) {
                decoded[write_ptr++] = 0;
            }
        }
        
        // Create result array of the correct size
        byte[] result = new byte[write_ptr];
        System.arraycopy(decoded, 0, result, 0, write_ptr);
        
        return result;
    }
}
