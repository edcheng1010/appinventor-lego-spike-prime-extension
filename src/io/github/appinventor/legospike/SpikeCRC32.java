package io.github.appinventor.legospike;

import java.util.zip.CRC32;

/**
 * CRC32 utility for LEGO SPIKE Prime protocol
 * 
 * Implements CRC32 calculation according to the SPIKE™ Prime Protocol 1.0 specification,
 * which requires padding data to multiples of 4 bytes before calculation.
 */
public class SpikeCRC32 {
    
    /**
     * Calculate CRC32 checksum according to SPIKE™ Prime protocol requirements
     * 
     * For SPIKE™ Prime, the CRC must be calculated on a multiple of 4 bytes.
     * For data that is not a multiple of 4 bytes, append 0x00 until the data
     * is a multiple of 4 bytes before calculating the CRC.
     * 
     * @param data The data to calculate CRC32 for
     * @return The CRC32 checksum as a long
     */
    public static long calculateCRC32(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }
        
        // Determine if padding is needed
        int remainder = data.length % 4;
        byte[] paddedData;
        
        if (remainder == 0) {
            // No padding needed
            paddedData = data;
        } else {
            // Pad with zeros to make multiple of 4 bytes
            int paddingNeeded = 4 - remainder;
            paddedData = new byte[data.length + paddingNeeded];
            System.arraycopy(data, 0, paddedData, 0, data.length);
            // Padding bytes are already initialized to 0
        }
        
        // Calculate CRC32
        CRC32 crc = new CRC32();
        crc.update(paddedData);
        return crc.getValue();
    }
    
    /**
     * Calculate CRC32 checksum and return as byte array
     * 
     * @param data The data to calculate CRC32 for
     * @return The CRC32 checksum as a 4-byte array
     */
    public static byte[] calculateCRC32Bytes(byte[] data) {
        long crcValue = calculateCRC32(data);
        byte[] result = new byte[4];
        result[0] = (byte) (crcValue & 0xFF);
        result[1] = (byte) ((crcValue >> 8) & 0xFF);
        result[2] = (byte) ((crcValue >> 16) & 0xFF);
        result[3] = (byte) ((crcValue >> 24) & 0xFF);
        return result;
    }
    
    /**
     * Append CRC32 checksum to data
     * 
     * @param data The data to append CRC32 to
     * @return The data with CRC32 appended
     */
    public static byte[] appendCRC32(byte[] data) {
        byte[] crc = calculateCRC32Bytes(data);
        byte[] result = new byte[data.length + crc.length];
        System.arraycopy(data, 0, result, 0, data.length);
        System.arraycopy(crc, 0, result, data.length, crc.length);
        return result;
    }
    
    /**
     * Verify CRC32 checksum in data
     * 
     * @param data The data with CRC32 in the last 4 bytes
     * @return true if CRC32 is valid, false otherwise
     */
    public static boolean verifyCRC32(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        
        byte[] dataWithoutCRC = new byte[data.length - 4];
        byte[] providedCRC = new byte[4];
        
        System.arraycopy(data, 0, dataWithoutCRC, 0, data.length - 4);
        System.arraycopy(data, data.length - 4, providedCRC, 0, 4);
        
        byte[] calculatedCRC = calculateCRC32Bytes(dataWithoutCRC);
        
        // Compare CRCs
        for (int i = 0; i < 4; i++) {
            if (providedCRC[i] != calculatedCRC[i]) {
                return false;
            }
        }
        
        return true;
    }
}
