package io.github.appinventor.legospike;

/**
 * MessageSender class for LEGO SPIKE Prime extension.
 * 
 * This class handles sending messages to the SPIKE Prime hub.
 * 
 * Enhanced with improved error handling and debug logging.
 */
public class MessageSender {
    
    // Callback interface for sending bytes
    public interface Callback {
        void sendBytes(byte[] bytes);
    }
    
    // Callback instance
    private final Callback callback;
    private boolean debugMode = false;
    
    /**
     * Constructor for MessageSender.
     * 
     * @param callback the callback to use for sending bytes
     */
    public MessageSender(Callback callback) {
        this.callback = callback;
    }
    
    /**
     * Sets debug mode
     * 
     * @param debugMode true to enable debug mode, false otherwise
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /**
     * Send a message to the hub.
     * 
     * @param message the message bytes
     */
    public void sendMessage(byte[] message) {
        if (message == null || message.length == 0) {
            logDebug("Attempted to send null or empty message");
            return;
        }
        
        try {
            // Encode the message using COBS and add delimiters
            byte[] encoded = COBSEncoder.encode(message, true);
            
            // Send the encoded message
            callback.sendBytes(encoded);
            
            logDebug("Message sent: " + bytesToHex(message));
        } catch (Exception e) {
            logError("Error sending message: " + e.getMessage());
        }
    }
    
    /**
     * Send a low-priority message to the hub.
     * 
     * @param message the message bytes
     */
    public void sendLowPriorityMessage(byte[] message) {
        if (message == null || message.length == 0) {
            logDebug("Attempted to send null or empty low-priority message");
            return;
        }
        
        try {
            // Encode the message using COBS and add delimiters (not high priority)
            byte[] encoded = COBSEncoder.encode(message, false);
            
            // Send the encoded message
            callback.sendBytes(encoded);
            
            logDebug("Low-priority message sent: " + bytesToHex(message));
        } catch (Exception e) {
            logError("Error sending low-priority message: " + e.getMessage());
        }
    }
    
    /**
     * Converts a byte array to a hexadecimal string for debugging
     * 
     * @param bytes the byte array
     * @return the hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
    
    /**
     * Logs a debug message
     * 
     * @param message the message to log
     */
    private void logDebug(String message) {
        if (debugMode) {
            android.util.Log.d("MessageSender", message);
        }
    }
    
    /**
     * Logs an error message
     * 
     * @param message the error message
     */
    private void logError(String message) {
        android.util.Log.e("MessageSender", message);
    }
}
