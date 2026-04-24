package io.github.appinventor.legospike;

/**
 * Message builder utility for LEGO SPIKE Prime protocol
 * 
 * Implements message construction with proper CRC32, string handling, and array handling
 * according to the SPIKE™ Prime Protocol 1.0 specification.
 */
public class SpikeMessageBuilder {
    
    /**
     * Create a complete message with proper encoding and CRC32
     * 
     * @param messageType The message type
     * @param payload The message payload
     * @return The complete message ready for transmission
     */
    public static byte[] createMessage(byte messageType, byte[] payload) {
        // Create the message without CRC
        byte[] messageWithoutCRC = createMessageWithoutCRC(messageType, payload);
        
        // Add CRC32
        return SpikeCRC32.appendCRC32(messageWithoutCRC);
    }
    
    /**
     * Create a message without CRC32
     * 
     * @param messageType The message type
     * @param payload The message payload
     * @return The message without CRC32
     */
    private static byte[] createMessageWithoutCRC(byte messageType, byte[] payload) {
        int payloadLength = (payload != null) ? payload.length : 0;
        byte[] message = new byte[payloadLength + 2]; // +2 for length and message type
        
        message[0] = (byte) (payloadLength + 2); // Length includes itself and message type
        message[1] = messageType;
        
        if (payloadLength > 0) {
            System.arraycopy(payload, 0, message, 2, payloadLength);
        }
        
        return message;
    }
    
    /**
     * Create a hub info request message
     * 
     * @return The complete message ready for transmission
     */
    public static byte[] createHubInfoRequest() {
        return createMessage((byte) 0x00, null); // InfoRequest has no payload
    }
    
    /**
     * Create a set hub name message
     * 
     * @param name The new hub name
     * @return The complete message ready for transmission
     */
    public static byte[] createSetHubNameRequest(String name) {
        byte[] nameBytes = SpikeStringUtil.toNullTerminatedString(name, 32); // Max 32 chars including null
        return createMessage((byte) 0x16, nameBytes); // SetHubNameRequest
    }
    
    /**
     * Create a get hub name request message
     * 
     * @return The complete message ready for transmission
     */
    public static byte[] createGetHubNameRequest() {
        return createMessage((byte) 0x18, null); // GetHubNameRequest has no payload
    }
    
    /**
     * Create a motor power command
     * 
     * @param portLetter The port letter (A-F)
     * @param power The power level (-100 to 100)
     * @return The complete message ready for transmission
     */
    public static byte[] createMotorPowerCommand(char portLetter, int power) {
        int portNumber = SpikePortMap.portLetterToNumber(portLetter);
        if (portNumber < 0) {
            throw new IllegalArgumentException("Invalid port letter: " + portLetter);
        }
        
        byte[] payload = new byte[6];
        payload[0] = 0x00; // Hub ID (0 = default)
        payload[1] = SpikeProtocol.PORT_OUTPUT_COMMAND; // Message type
        payload[2] = (byte) portNumber; // Port number
        payload[3] = 0x00; // Startup/completion information
        payload[4] = SpikeProtocol.PORT_OUTPUT_SUBCOMMAND_START_POWER; // Subcommand
        payload[5] = (byte) power; // Power
        
        return createMessage((byte) 0x0A, payload); // PORT_OUTPUT_COMMAND
    }
    
    /**
     * Create a motor degrees command
     * 
     * @param portLetter The port letter (A-F)
     * @param power The power level (-100 to 100)
     * @param degrees The number of degrees to rotate
     * @return The complete message ready for transmission
     */
    public static byte[] createMotorDegreesCommand(char portLetter, int power, int degrees) {
        int portNumber = SpikePortMap.portLetterToNumber(portLetter);
        if (portNumber < 0) {
            throw new IllegalArgumentException("Invalid port letter: " + portLetter);
        }
        
        byte[] payload = new byte[10];
        payload[0] = 0x00;  // Hub ID
        payload[1] = SpikeProtocol.PORT_OUTPUT_COMMAND;
        payload[2] = (byte) (portNumber & 0xFF);
        payload[3] = SpikeProtocol.PORT_OUTPUT_STARTUP_EXECUTE_IMMEDIATELY;
        payload[4] = SpikeProtocol.PORT_OUTPUT_COMPLETION_FEEDBACK;
        payload[5] = SpikeProtocol.PORT_OUTPUT_SUBCOMMAND_START_SPEED_FOR_DEGREES;
        payload[6] = (byte) (degrees & 0xFF);
        payload[7] = (byte) ((degrees >> 8) & 0xFF);
        payload[8] = (byte) ((degrees >> 16) & 0xFF);
        payload[9] = (byte) (power & 0xFF);
        
        return createMessage((byte) 0x0A, payload); // PORT_OUTPUT_COMMAND
    }
    
    /**
     * Create a set hub LED color command
     * 
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return The complete message ready for transmission
     */
    public static byte[] createSetHubLEDCommand(int r, int g, int b) {
        byte[] payload = new byte[8];
        payload[0] = 0x00; // Hub ID (0 = default)
        payload[1] = SpikeProtocol.PORT_OUTPUT_COMMAND; // Message type
        payload[2] = 0x32; // Port number for LED (50)
        payload[3] = 0x00; // Startup/completion information
        payload[4] = 0x03; // Subcommand for RGB LED
        payload[5] = (byte) r; // Red
        payload[6] = (byte) g; // Green
        payload[7] = (byte) b; // Blue
        
        return createMessage((byte) 0x0A, payload); // PORT_OUTPUT_COMMAND
    }
    
    /**
     * Create a play tone command
     * 
     * @param frequency The frequency in Hz
     * @param duration The duration in milliseconds
     * @return The complete message ready for transmission
     */
    public static byte[] createPlayToneCommand(int frequency, int duration) {
        byte[] payload = new byte[10];
        payload[0] = 0x00; // Hub ID (0 = default)
        payload[1] = SpikeProtocol.PORT_OUTPUT_COMMAND; // Message type
        payload[2] = 0x01; // Port number for speaker
        payload[3] = 0x00; // Startup/completion information
        payload[4] = 0x03; // Subcommand for tone
        payload[5] = (byte) (frequency & 0xFF); // Frequency (LSB)
        payload[6] = (byte) ((frequency >> 8) & 0xFF); // Frequency (MSB)
        payload[7] = (byte) (duration & 0xFF); // Duration (LSB)
        payload[8] = (byte) ((duration >> 8) & 0xFF); // Duration (MSB)
        payload[9] = 0x01; // Volume (0-10)
        
        return createMessage((byte) 0x0A, payload); // PORT_OUTPUT_COMMAND
    }
    
    /**
     * Parse a message and verify its CRC32
     * 
     * @param data The message data
     * @return The parsed message without CRC32, or null if CRC is invalid
     */
    public static byte[] parseMessage(byte[] data) {
        if (data == null || data.length < 6) { // Minimum: 1 byte length + 1 byte type + 4 bytes CRC32
            return null;
        }
        
        // Verify CRC32
        if (!SpikeCRC32.verifyCRC32(data)) {
            return null;
        }
        
        // Extract message without CRC
        byte[] messageWithoutCRC = new byte[data.length - 4];
        System.arraycopy(data, 0, messageWithoutCRC, 0, data.length - 4);
        
        return messageWithoutCRC;
    }
    
    /**
     * Extract the message type from a parsed message
     * 
     * @param message The parsed message (without CRC)
     * @return The message type, or -1 if invalid
     */
    public static int getMessageType(byte[] message) {
        if (message == null || message.length < 2) {
            return -1;
        }
        
        return message[1] & 0xFF;
    }
    
    /**
     * Extract the payload from a parsed message
     * 
     * @param message The parsed message (without CRC)
     * @return The payload, or empty array if none
     */
    public static byte[] getPayload(byte[] message) {
        if (message == null || message.length <= 2) {
            return new byte[0];
        }
        
        byte[] payload = new byte[message.length - 2];
        System.arraycopy(message, 2, payload, 0, payload.length);
        
        return payload;
    }
    
    /**
     * Parse a hub info response
     * 
     * @param message The parsed message (without CRC)
     * @return The hub info, or null if invalid
     */
    public static SpikeProtocol.HubInfo parseHubInfoResponse(byte[] message) {
        if (message == null || message.length < 10 || getMessageType(message) != 0x01) {
            return null;
        }
        
        byte[] payload = getPayload(message);
        
        if (payload[0] != SpikeProtocol.HUB_PROPERTIES || 
            payload[1] != SpikeProtocol.HUB_PROPERTY_OPERATION_RESPONSE || 
            payload[2] != SpikeProtocol.HUB_PROPERTY_FW_VERSION) {
            return null;
        }
        
        int hubType = payload[3] & 0xFF;
        
        // Parse firmware version
        StringBuilder firmwareVersion = new StringBuilder();
        firmwareVersion.append(payload[4] & 0xFF).append(".");
        firmwareVersion.append(payload[5] & 0xFF).append(".");
        firmwareVersion.append(payload[6] & 0xFF).append(".");
        firmwareVersion.append(payload[7] & 0xFF);
        
        // Default values for name and battery
        String hubName = "LEGO Prime Hub";
        int batteryLevel = 100;
        
        return new SpikeProtocol.HubInfo(hubType, hubName, firmwareVersion.toString(), batteryLevel);
    }
    
    /**
     * Parse a hub name response
     * 
     * @param message The parsed message (without CRC)
     * @return The hub name, or null if invalid
     */
    public static String parseHubNameResponse(byte[] message) {
        if (message == null || message.length < 3 || getMessageType(message) != 0x19) {
            return null;
        }
        
        byte[] payload = getPayload(message);
        return SpikeStringUtil.fromNullTerminatedString(payload);
    }
}
