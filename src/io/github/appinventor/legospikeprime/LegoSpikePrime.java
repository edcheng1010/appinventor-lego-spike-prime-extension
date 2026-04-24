package io.github.appinventor.legospikeprime;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.YailList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * LegoSpikePrime extension for MIT App Inventor
 * 
 * This extension provides functionality for discovering and connecting to LEGO SPIKE Prime hubs
 * using the App Inventor BluetoothLE component's index-based device access model.
 * This version uses RSSI staleness detection to hide cached devices without blacklisting.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 1,
    description = "Extension for communicating with LEGO SPIKE Prime hubs",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikePrime extends AndroidNonvisibleComponent {

    private static final String LOG_TAG = "LegoSpikePrime";
    
    // BluetoothLE component reference
    private Component bluetoothLE;
    
    // BluetoothInterface for handling connection and communication
    private BluetoothInterfaceImpl bluetoothInterface;
    
    // Properties
    private boolean debugMode = true;
    private boolean isConnected = false;
    private String connectedDeviceAddress = "";
    private String connectedDeviceName = "";
    private String customDeviceName = "LEGO Hub"; // Default value
    private boolean isScanning = false; // Track scanning state
    private boolean wasScanningBeforeConnection = false; // Track if we should restart scanning
    private int scanInterval = 1000; // Scan interval in milliseconds (1 second)
    private Timer scanTimer; // Timer for real-time scanning
    
    // Handler for main thread event dispatching
    private Handler mainHandler;
    
    // List to store LEGO SPIKE Prime hubs only (includes both visible and hidden hubs)
    private List<LegoHub> legoHubs = new ArrayList<>();
    
    // Previous hub state for change tracking
    private List<LegoHub> previousLegoHubs = new ArrayList<>();
    
    // Known LEGO SPIKE Prime hub names
    private static final Set<String> LEGO_HUB_NAMES = new HashSet<>(Arrays.asList(
        "MITNodeHub",
        "LEGO Technic Hub",
        "LEGO Hub",
        "SPIKE Prime Hub",
        "SPIKE Hub"
    ));
    
    /**
     * Class to represent a LEGO hub
     */
    private class LegoHub {
        private String name;
        private String address;
        private int bleIndex;
        private long lastSeenTimestamp;
        private int lastRssi;
        private int rssiStaleCount;
        
        public LegoHub(String name, String address, int bleIndex) {
            this.name = name;
            this.address = address;
            this.bleIndex = bleIndex;
            this.lastSeenTimestamp = System.currentTimeMillis();
            this.lastRssi = Integer.MIN_VALUE; // Initialize with invalid RSSI
            this.rssiStaleCount = 0;
        }
        
        // Copy constructor for creating snapshots with frozen visibility state
        public LegoHub(LegoHub other, boolean frozenVisibility) {
            this.name = other.name;
            this.address = other.address;
            this.bleIndex = other.bleIndex;
            this.lastSeenTimestamp = other.lastSeenTimestamp;
            this.lastRssi = other.lastRssi;
            this.rssiStaleCount = other.rssiStaleCount;
            this.frozenVisibility = frozenVisibility;
        }
        
        private Boolean frozenVisibility = null; // For snapshot copies
        
        public String getName() {
            return name;
        }
        
        public String getAddress() {
            return address;
        }
        
        public int getBleIndex() {
            return bleIndex;
        }
        
        public long getLastSeenTimestamp() {
            return lastSeenTimestamp;
        }
        
        public void updateLastSeen() {
            this.lastSeenTimestamp = System.currentTimeMillis();
        }
        
        public boolean updateRssi(int newRssi) {
            if (this.lastRssi == newRssi) {
                this.rssiStaleCount++;
                return false; // RSSI hasn't changed
            } else {
                this.lastRssi = newRssi;
                this.rssiStaleCount = 0;
                return true; // RSSI changed
            }
        }
        
        public boolean isRssiStale(int maxStaleCount) {
            return this.rssiStaleCount >= maxStaleCount;
        }
        
        public boolean isVisible() {
            // If this is a snapshot copy, return the frozen visibility state
            if (frozenVisibility != null) {
                return frozenVisibility;
            }
            
            // Hybrid approach: Device is hidden if BOTH RSSI is stale AND timestamp is stale
            boolean rssiStale = this.rssiStaleCount >= 3;
            boolean timestampStale = (System.currentTimeMillis() - this.lastSeenTimestamp) > (2 * LegoSpikePrime.this.scanInterval);
            return !(rssiStale && timestampStale);
        }
        
        public int getLastRssi() {
            return this.lastRssi;
        }
        
        @Override
        public String toString() {
            return name + " (" + address + ")";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            LegoHub other = (LegoHub) obj;
            return address.equals(other.address);
        }
        
        @Override
        public int hashCode() {
            return address.hashCode();
        }
    }
    
    /**
     * Constructor for the LegoSpikePrime extension
     * 
     * @param container the container this component will be placed in
     */
    public LegoSpikePrime(ComponentContainer container) {
        super(container.$form());
        mainHandler = new Handler(Looper.getMainLooper());
        bluetoothInterface = new BluetoothInterfaceImpl();
        bluetoothInterface.setExtension(this);
        logDebug("LegoSpikePrime extension initialized");
    }
    
    /**
     * Log debug messages if debug mode is enabled
     * 
     * @param message the message to log
     */
    private void logDebug(String message) {
        if (debugMode) {
            Log.d(LOG_TAG, message);
        }
    }
    
    /**
     * Set the BluetoothLE component to use for communication
     * 
     * @param bluetoothLE the BluetoothLE component
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "The BluetoothLE component used for communication with BLE devices")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT + 
                      ":edu.mit.appinventor.ble.BluetoothLE")
    public void BluetoothDevice(Component bluetoothLE) {
        this.bluetoothLE = bluetoothLE;
        bluetoothInterface.setBluetoothLE(bluetoothLE);
        logDebug("BluetoothLE component set: " + bluetoothLE);
        
        // Register for BluetoothLE events
        try {
            // Register for DeviceFound event
            java.lang.reflect.Method registerEventMethod = 
                bluetoothLE.getClass().getMethod("DeviceFound", Object.class, String.class, String.class, int.class);
            registerEventMethod.invoke(bluetoothLE, this, "BluetoothLE_DeviceFound", "%s %s %s", 3);
            
            // Register for ScanningStateChanged event
            registerEventMethod = 
                bluetoothLE.getClass().getMethod("ScanningStateChanged", Object.class, String.class, String.class, int.class);
            registerEventMethod.invoke(bluetoothLE, this, "BluetoothLE_ScanningStateChanged", "%s", 1);
            
            // Register for Connected event
            registerEventMethod = 
                bluetoothLE.getClass().getMethod("Connected", Object.class, String.class, String.class, int.class);
            registerEventMethod.invoke(bluetoothLE, this, "BluetoothLE_Connected", "%s", 1);
            
            // Register for Disconnected event
            registerEventMethod = 
                bluetoothLE.getClass().getMethod("Disconnected", Object.class, String.class, String.class, int.class);
            registerEventMethod.invoke(bluetoothLE, this, "BluetoothLE_Disconnected", "", 0);
            
            logDebug("Successfully registered for BluetoothLE events");
        } catch (Exception e) {
            logDebug("Error registering for BluetoothLE events: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Get the BluetoothLE component
     * 
     * @return the BluetoothLE component
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "The BluetoothLE component used for communication with BLE devices")
    public Component BluetoothDevice() {
        return bluetoothLE;
    }
    
    /**
     * Set debug mode
     * 
     * @param enabled whether debug mode is enabled
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Whether debug mode is enabled")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
                     defaultValue = "True")
    public void DebugMode(boolean enabled) {
        debugMode = enabled;
        logDebug("Debug mode set to: " + enabled);
    }
    
    /**
     * Get debug mode
     * 
     * @return whether debug mode is enabled
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Whether debug mode is enabled")
    public boolean DebugMode() {
        return debugMode;
    }
    
    /**
     * Set the custom device name to detect
     * 
     * @param deviceName the custom device name to detect
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Custom device name to detect (case insensitive)")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
                     defaultValue = "LEGO Hub")
    public void CustomDeviceName(String deviceName) {
        this.customDeviceName = deviceName;
        logDebug("Custom device name set to: " + deviceName);
    }
    
    /**
     * Get the custom device name to detect
     * 
     * @return the custom device name to detect
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Custom device name to detect (case insensitive)")
    public String CustomDeviceName() {
        return customDeviceName;
    }
    
    /**
     * Set the scan interval for real-time scanning
     * 
     * @param interval the scan interval in milliseconds
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Scan interval in milliseconds for real-time scanning")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER,
                     defaultValue = "1000")
    public void ScanInterval(int interval) {
        if (interval < 100) {
            interval = 100; // Minimum 100ms to avoid excessive scanning
        }
        this.scanInterval = interval;
        logDebug("Scan interval set to: " + interval + "ms");
    }
    
    /**
     * Get the scan interval for real-time scanning
     * 
     * @return the scan interval in milliseconds
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Scan interval in milliseconds for real-time scanning")
    public int ScanInterval() {
        return scanInterval;
    }
    
    /**
     * Get whether the extension is currently scanning for devices
     * 
     * @return whether the extension is currently scanning
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Whether the extension is currently scanning for devices")
    public boolean IsScanning() {
        return isScanning;
    }
    
    /**
     * Get whether the extension is connected to a hub
     * 
     * @return whether the extension is connected to a hub
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "Whether the extension is connected to a hub")
    public boolean IsConnected() {
        return isConnected;
    }
    
    /**
     * Get the name of the connected device
     * 
     * @return the name of the connected device
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "The name of the connected device")
    public String ConnectedDeviceName() {
        return connectedDeviceName;
    }
    
    /**
     * Get the address of the connected device
     * 
     * @return the address of the connected device
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "The address of the connected device")
    public String ConnectedDeviceAddress() {
        return connectedDeviceAddress;
    }


    
    /**
     * Start scanning for LEGO SPIKE Prime hubs
     * This method starts scanning for BLE devices using the BluetoothLE component
     * and sets up a timer for real-time scanning
     */
    @SimpleFunction(description = "Start scanning for LEGO SPIKE Prime hubs")
    public void StartScanning() {
        if (bluetoothLE == null) {
            logDebug("Error: BluetoothLE component not set");
            ErrorOccurred("BluetoothLE component not set");
            return;
        }
        
        if (isScanning) {
            logDebug("Already scanning, stopping current scan first");
            StopScanning();
        }
        
        // Store previous state before clearing for change tracking
        List<LegoHub> oldHubs = new ArrayList<>(legoHubs);
        
        // Clear the list of LEGO hubs (Option 4: Clear device list when scanning starts)
        legoHubs.clear();
        previousLegoHubs.clear();
        logDebug("Cleared device list for fresh scan");
        
        // Trigger HubListChanged event if there were devices that got cleared
        if (!oldHubs.isEmpty()) {
            String newHubs = "";
            String retainedHubs = "";
            String lostHubs = buildHubListString(oldHubs);
            String allCurrentHubs = "";
            logDebug("Triggering HubListChanged after clearing device list: lost=[" + lostHubs + "]");
            HubListChanged(newHubs, retainedHubs, lostHubs, allCurrentHubs);
        }
        
        logDebug("Starting scan for LEGO SPIKE Prime hubs");
        try {
            // Call the StartScanning method on the BluetoothLE component
            java.lang.reflect.Method startScanningMethod = 
                bluetoothLE.getClass().getMethod("StartScanning");
            startScanningMethod.invoke(bluetoothLE);
            
            // Set scanning state
            isScanning = true;
            
            // Start timer for real-time scanning
            startScanTimer();
            
            logDebug("Scan started successfully");
            
            // Trigger the ScanningStarted event
            ScanningStarted();
        } catch (Exception e) {
            logDebug("Error starting scan: " + e.getMessage());
            e.printStackTrace();
            ErrorOccurred("Error starting scan: " + e.getMessage());
        }
    }
    
    /**
     * Stop scanning for LEGO SPIKE Prime hubs
     * This method stops scanning for BLE devices and cancels the scan timer
     */
    @SimpleFunction(description = "Stop scanning for LEGO SPIKE Prime hubs")
    public void StopScanning() {
        if (bluetoothLE == null) {
            logDebug("Error: BluetoothLE component not set");
            return;
        }
        
        if (!isScanning) {
            logDebug("Not currently scanning");
            return;
        }
        
        logDebug("Stopping scan for LEGO SPIKE Prime hubs");
        try {
            // Call the StopScanning method on the BluetoothLE component
            java.lang.reflect.Method stopScanningMethod = 
                bluetoothLE.getClass().getMethod("StopScanning");
            stopScanningMethod.invoke(bluetoothLE);
            
            // Stop the scan timer
            stopScanTimer();
            
            // Set scanning state
            isScanning = false;
            
            logDebug("Scan stopped successfully");
            
            // Trigger the ScanningStopped event
            ScanningStopped();
        } catch (Exception e) {
            logDebug("Error stopping scan: " + e.getMessage());
            e.printStackTrace();
            ErrorOccurred("Error stopping scan: " + e.getMessage());
        }
    }
    
    /**
     * Start the scan timer for real-time scanning
     */
    private void startScanTimer() {
        // Cancel any existing timer
        stopScanTimer();
        
        // Create a new timer
        scanTimer = new Timer();
        
        // Schedule a timer task to check for devices periodically
        scanTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isScanning) {
                    // Check for devices - use the correct method name with capital C
                    CheckAllDevices();
                }
            }
        }, scanInterval, scanInterval);
        
        logDebug("Scan timer started with interval: " + scanInterval + "ms");
    }
    
    /**
     * Stop the scan timer
     */
    private void stopScanTimer() {
        if (scanTimer != null) {
            scanTimer.cancel();
            scanTimer = null;
            logDebug("Scan timer stopped");
        }
    }
    
    /**
     * Check if a device at the given index is a LEGO SPIKE Prime hub
     * 
     * @param index the index of the hub in the LEGO hubs list (1-based)
     * @return true if the device is a LEGO SPIKE Prime hub
     */
    @SimpleFunction(description = "Check if a device at the given index in the LEGO hubs list is a LEGO SPIKE Prime hub (1-based)")
    public boolean CheckDeviceAtIndex(int index) {
        if (bluetoothLE == null) {
            logDebug("Error: BluetoothLE component not set");
            return false;
        }
        
        List<LegoHub> visibleHubs = getVisibleHubs();
        if (index < 1 || index > visibleHubs.size()) {
            logDebug("Error: Invalid hub index: " + index + ". Valid range: 1 to " + visibleHubs.size());
            return false;
        }
        
        try {
            // Get the hub from our visible list
            LegoHub hub = visibleHubs.get(index - 1);
            int bleIndex = hub.getBleIndex(); // Get the stored BLE index
            
            // Get the device name at the BLE index
            java.lang.reflect.Method getDeviceNameMethod = 
                bluetoothLE.getClass().getMethod("FoundDeviceName", int.class);
            String deviceName = (String) getDeviceNameMethod.invoke(bluetoothLE, bleIndex);
            
            // Get the device address at the BLE index
            java.lang.reflect.Method getDeviceAddressMethod = 
                bluetoothLE.getClass().getMethod("FoundDeviceAddress", int.class);
            String deviceAddress = (String) getDeviceAddressMethod.invoke(bluetoothLE, bleIndex);
            
            logDebug("Checking hub at filtered index " + index + " (BLE index " + bleIndex + "): " + deviceName + " (" + deviceAddress + ")");
            
            // Verify this is still a LEGO SPIKE Prime hub
            boolean isLegoHub = isLegoSpikeHub(deviceName);
            
            if (isLegoHub) {
                logDebug("LEGO SPIKE Prime hub confirmed: " + deviceName);
                return true;
            } else {
                logDebug("Device is no longer a LEGO SPIKE Prime hub: " + deviceName);
                return false;
            }
        } catch (Exception e) {
            logDebug("Error checking device at filtered index " + index + ": " + e);
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Check all devices for LEGO SPIKE Prime hubs and track changes
     */
    @SimpleFunction(description = "Check all devices for LEGO SPIKE Prime hubs")
    public void CheckAllDevices() {
        if (bluetoothLE == null) {
            logDebug("Error: BluetoothLE component not set");
            return;
        }
        
        try {
            // Store previous state for change tracking with frozen visibility snapshots
            List<LegoHub> oldHubs = new ArrayList<>();
            for (LegoHub hub : legoHubs) {
                // Create snapshot with current visibility state frozen
                oldHubs.add(new LegoHub(hub, hub.isVisible()));
            }
            
            // REMOVED: Timeout-based removal logic - now using RSSI staleness detection
            // Devices are hidden via isVisible() method instead of being removed
            
            // Get the device list from the BluetoothLE component
            java.lang.reflect.Method getDeviceListMethod = 
                bluetoothLE.getClass().getMethod("DeviceList");
            String deviceListStr = (String) getDeviceListMethod.invoke(bluetoothLE);
            
            logDebug("Device list: " + deviceListStr);
            
            if (deviceListStr == null || deviceListStr.isEmpty()) {
                logDebug("No devices found to check");
                // Calculate visibility changes for empty device list (consistent with visible-only logic)
                List<LegoHub> visibleHubs = getVisibleHubs();
                List<LegoHub> previousVisibleHubs = getVisibleHubsFromList(oldHubs);
                String newHubs = "";
                String retainedHubs = buildHubListString(visibleHubs);
                String lostHubs = buildLostHubsString(previousVisibleHubs, visibleHubs);
                String allCurrentHubs = LegoHubsList();
                
                // Trigger HubListChanged event if there are any changes
                if (!lostHubs.isEmpty()) {
                    HubListChanged(newHubs, retainedHubs, lostHubs, allCurrentHubs);
                }
                return;
            }
            
            // Split the device list by commas to get individual device entries
            String[] deviceEntries = deviceListStr.split(",");
            int deviceCount = deviceEntries.length;
            
            logDebug("Found " + deviceCount + " total BLE devices");
            
            // Mark all current hubs as not seen in this scan
            Set<String> seenAddresses = new HashSet<>();
            
            // Check each device in the list
            // Note: This uses BLE device indexes (1-based) for internal scanning
            for (int i = 1; i <= deviceCount; i++) {
                logDebug("Checking BLE device " + i + " of " + deviceCount);
                String deviceAddress = checkBLEDeviceAtIndex(i, seenAddresses); // Updated method signature
            }
            
            // REMOVED: Timestamp updates based on BLE cache (causes timeout to never trigger)
            // Timestamps are now only updated when devices are actively discovered via BluetoothLE_DeviceFound
            
            // Calculate visibility changes between scans (SIMPLE ADDRESS COMPARISON - NO HASHMAP)
            List<LegoHub> visibleOldHubs = getVisibleHubsFromList(oldHubs);
            List<LegoHub> visibleCurrentHubs = getVisibleHubs();
            
            // Calculate changes and build hub lists (BASED ON WORKING VERSION LOGIC)
            List<LegoHub> newHubsList = new ArrayList<>();
            List<LegoHub> retainedHubsList = new ArrayList<>();
            List<LegoHub> lostHubsList = new ArrayList<>();
            
            // Count new and retained visible devices
            for (LegoHub currentHub : visibleCurrentHubs) {
                boolean wasPresent = false;
                for (LegoHub oldHub : visibleOldHubs) {
                    if (currentHub.getAddress() != null && oldHub.getAddress() != null && 
                        currentHub.getAddress().equals(oldHub.getAddress())) {
                        wasPresent = true;
                        break;
                    }
                }
                if (wasPresent) {
                    retainedHubsList.add(currentHub);
                } else {
                    newHubsList.add(currentHub);
                }
            }
            
            // Count lost visible devices
            for (LegoHub oldHub : visibleOldHubs) {
                boolean stillPresent = false;
                for (LegoHub currentHub : visibleCurrentHubs) {
                    if (oldHub.getAddress() != null && currentHub.getAddress() != null && 
                        oldHub.getAddress().equals(currentHub.getAddress())) {
                        stillPresent = true;
                        break;
                    }
                }
                if (!stillPresent) {
                    lostHubsList.add(oldHub);
                }
            }
            
            logDebug("Found " + legoHubs.size() + " total LEGO SPIKE Prime hubs (" + visibleCurrentHubs.size() + " visible)");
            
            // Trigger HubListChanged event if there are any visibility changes
            if (!newHubsList.isEmpty() || !lostHubsList.isEmpty()) {
                String newHubs = buildHubListString(newHubsList);
                String retainedHubs = buildHubListString(retainedHubsList);
                String lostHubs = buildHubListString(lostHubsList);
                String allCurrentHubs = LegoHubsList();
                HubListChanged(newHubs, retainedHubs, lostHubs, allCurrentHubs);
            }
            
        } catch (Exception e) {
            logDebug("Error checking devices: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Get the list of LEGO SPIKE Prime hubs as a string
     * 
     * @return the list of LEGO SPIKE Prime hubs as a string
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "The list of LEGO SPIKE Prime hubs as a string")
    public String LegoHubsList() {
        List<LegoHub> visibleHubs = getVisibleHubs();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (LegoHub hub : visibleHubs) {
            if (!first) {
                sb.append(",");
            }
            sb.append(hub.getName());
            first = false;
        }
        return sb.toString();
    }
    
    /**
     * Get the number of LEGO SPIKE Prime hubs found
     * 
     * @return the number of LEGO SPIKE Prime hubs found
     */
    @SimpleFunction(description = "Get the number of LEGO SPIKE Prime hubs found")
    public int GetLegoHubCount() {
        return getVisibleHubs().size();
    }
    
    /**
     * Get the name of a LEGO SPIKE Prime hub at the given index
     * 
     * @param index the index of the hub in the LEGO hubs list (1-based)
     * @return the name of the hub
     */
    @SimpleFunction(description = "Get the name of a LEGO SPIKE Prime hub at the given index in the LEGO hubs list (1-based)")
    public String GetLegoHubName(int index) {
        List<LegoHub> visibleHubs = getVisibleHubs();
        if (index < 1 || index > visibleHubs.size()) {
            logDebug("Error: Invalid hub index: " + index + ". Valid range: 1 to " + visibleHubs.size());
            return "";
        }
        
        return visibleHubs.get(index - 1).getName();
    }
    
    /**
     * Get the address of a LEGO SPIKE Prime hub at the given index
     * 
     * @param index the index of the hub in the LEGO hubs list (1-based)
     * @return the address of the hub
     */
    @SimpleFunction(description = "Get the address of a LEGO SPIKE Prime hub at the given index in the LEGO hubs list (1-based)")
    public String GetLegoHubAddress(int index) {
        List<LegoHub> visibleHubs = getVisibleHubs();
        if (index < 1 || index > visibleHubs.size()) {
            logDebug("Error: Invalid hub index: " + index + ". Valid range: 1 to " + visibleHubs.size());
            return "";
        }
        
        return visibleHubs.get(index - 1).getAddress();
    }


    
    /**
     * Connect to a LEGO SPIKE Prime hub at the given index in the LEGO hubs list
     * 
     * @param index the index of the hub in the LEGO hubs list (1-based)
     * @return true if the connection was initiated successfully
     */
    @SimpleFunction(description = "Connect to a LEGO SPIKE Prime hub at the given index in the LEGO hubs list (1-based)")
    public boolean ConnectToHub(int index) {
        if (bluetoothLE == null) {
            logDebug("Error: BluetoothLE component not set");
            ErrorOccurred("BluetoothLE component not set");
            return false;
        }
        
        List<LegoHub> visibleHubs = getVisibleHubs();
        if (index < 1 || index > visibleHubs.size()) {
            logDebug("Error: Invalid hub index: " + index + ". Valid range: 1 to " + visibleHubs.size());
            ErrorOccurred("Invalid hub index: " + index + ". Valid range: 1 to " + visibleHubs.size());
            return false;
        }
        
        if (isConnected) {
            logDebug("Already connected to a hub, disconnecting first");
            Disconnect();
        }
        
        // Stop scanning during connection attempt
        if (isScanning) {
            wasScanningBeforeConnection = true;
            stopScanTimer();
            isScanning = false;  // Update scanning state
            ScanningStopped();   // Trigger ScanningStopped event
            logDebug("Stopped scanning for connection attempt");
        } else {
            wasScanningBeforeConnection = false;
        }
        
        LegoHub hub = visibleHubs.get(index - 1);
        logDebug("Connecting to hub: " + hub.getName() + " (" + hub.getAddress() + ")");
        
        try {
            // Call the ConnectWithAddress method on the BluetoothLE component
            // This is the correct method name in the BluetoothLE component
            java.lang.reflect.Method connectMethod = 
                bluetoothLE.getClass().getMethod("ConnectWithAddress", String.class);
            
            // Convert the address to a String explicitly to avoid reflection errors
            String deviceAddress = hub.getAddress();
            connectMethod.invoke(bluetoothLE, deviceAddress);
            
            logDebug("Connection initiated to hub: " + hub.getName() + " (" + hub.getAddress() + ")");
            return true;
        } catch (Exception e) {
            logDebug("Error connecting to hub: " + e);
            e.printStackTrace();
            ErrorOccurred("Failed to connect: " + e);
            
            // Restart scanning if connection failed and we were scanning before
            if (wasScanningBeforeConnection) {
                logDebug("Restarting scanning after connection failure");
                isScanning = true;        // Update scanning state
                startScanTimer();
                ScanningStarted();        // Trigger ScanningStarted event
            }
            
            return false;
        }
    }
    
    /**
     * Disconnect from the currently connected hub
     */
    @SimpleFunction(description = "Disconnect from the currently connected hub")
    public void Disconnect() {
        if (!isConnected) {
            logDebug("Not connected to a hub");
            return;
        }
        
        logDebug("Disconnecting from hub: " + connectedDeviceName + " (" + connectedDeviceAddress + ")");
        
        try {
            // Call the Disconnect method on the BluetoothLE component
            java.lang.reflect.Method disconnectMethod = 
                bluetoothLE.getClass().getMethod("Disconnect");
            disconnectMethod.invoke(bluetoothLE);
            
            logDebug("Disconnection initiated from hub");
        } catch (Exception e) {
            logDebug("Error disconnecting from hub: " + e);
            e.printStackTrace();
            ErrorOccurred("Failed to disconnect: " + e);
        }
    }
    
    /**
     * Set the color of the hub LED
     * 
     * @param red the red component (0-255)
     * @param green the green component (0-255)
     * @param blue the blue component (0-255)
     * @return true if the command was sent successfully
     */
    @SimpleFunction(description = "Set the color of the hub LED")
    public boolean SetHubLEDColor(int red, int green, int blue) {
        if (!isConnected) {
            logDebug("Error: Not connected to a hub");
            ErrorOccurred("Not connected to a hub");
            return false;
        }
        
        logDebug("Setting hub LED color to RGB(" + red + ", " + green + ", " + blue + ")");
        
        // Create the LED command
        byte[] command = createSetLEDCommand(red, green, blue);
        
        // Send the command
        return bluetoothInterface.sendMessage(command);
    }
    
    /**
     * Run a motor at the specified port with the specified power
     * 
     * @param port the port letter (A-F)
     * @param power the power level (-100 to 100)
     * @return true if the command was sent successfully
     */
    @SimpleFunction(description = "Run a motor at the specified port with the specified power")
    public boolean RunMotor(String port, int power) {
        if (!isConnected) {
            logDebug("Error: Not connected to a hub");
            ErrorOccurred("Not connected to a hub");
            return false;
        }
        
        // Validate port letter
        if (port == null || port.isEmpty() || !port.matches("[A-Fa-f]")) {
            logDebug("Error: Invalid port letter: " + port);
            ErrorOccurred("Invalid port letter: " + port + ". Must be A-F.");
            return false;
        }
        
        // Validate power level
        if (power < -100 || power > 100) {
            logDebug("Error: Invalid power level: " + power);
            ErrorOccurred("Invalid power level: " + power + ". Must be between -100 and 100.");
            return false;
        }
        
        // Convert port letter to port number
        int portNumber = convertPortLetterToNumber(port.toUpperCase());
        
        logDebug("Running motor at port " + port + " (" + portNumber + ") with power " + power);
        
        // Create the motor command
        byte[] command = createMotorPowerCommand(portNumber, power);
        
        // Send the command
        return bluetoothInterface.sendMessage(command);
    }
    
    /**
     * Stop a motor at the specified port
     * 
     * @param port the port letter (A-F)
     * @return true if the command was sent successfully
     */
    @SimpleFunction(description = "Stop a motor at the specified port")
    public boolean StopMotor(String port) {
        return RunMotor(port, 0);
    }
    
    /**
     * Internal method to check a BLE device at a specific index and track seen addresses
     * @param bleIndex The BLE device index (1-based)
     * @param seenAddresses Set to track addresses seen in this scan
     * @return The device address if it's a LEGO hub, null otherwise
     */
    private String checkBLEDeviceAtIndex(int bleIndex, Set<String> seenAddresses) {
        if (bluetoothLE == null) {
            logDebug("Error: BluetoothLE component not set");
            return null;
        }
        
        try {
            // Get the device name at the given BLE index
            java.lang.reflect.Method getDeviceNameMethod = 
                bluetoothLE.getClass().getMethod("FoundDeviceName", int.class);
            String deviceName = (String) getDeviceNameMethod.invoke(bluetoothLE, bleIndex);
            
            // Get the device address at the given BLE index
            java.lang.reflect.Method getDeviceAddressMethod = 
                bluetoothLE.getClass().getMethod("FoundDeviceAddress", int.class);
            String deviceAddress = (String) getDeviceAddressMethod.invoke(bluetoothLE, bleIndex);
            
            // Get the device RSSI at the given BLE index
            java.lang.reflect.Method getDeviceRssiMethod = 
                bluetoothLE.getClass().getMethod("FoundDeviceRssi", int.class);
            Integer deviceRssi = (Integer) getDeviceRssiMethod.invoke(bluetoothLE, bleIndex);
            
            logDebug("Checking BLE device at index " + bleIndex + ": " + deviceName + " (" + deviceAddress + ")");
            
            // Check if the device name indicates a LEGO SPIKE Prime hub
            boolean isLegoHub = isLegoSpikeHub(deviceName);
            
            if (isLegoHub) {
                logDebug("LEGO SPIKE Prime hub found: " + deviceName);
                
                // Mark this address as seen in this scan
                seenAddresses.add(deviceAddress);
                
                // Check if the hub is already in our list
                boolean alreadyAdded = false;
                for (LegoHub hub : legoHubs) {
                    if (hub.getAddress().equals(deviceAddress)) {
                        alreadyAdded = true;
                        // Update the BLE index in case it changed
                        hub.bleIndex = bleIndex;
                        // Update RSSI and check for staleness
                        boolean rssiChanged = hub.updateRssi(deviceRssi);
                        if (rssiChanged) {
                            logDebug("RSSI updated for hub " + deviceName + ": " + deviceRssi);
                            // Only update timestamp when RSSI changes (indicating fresh detection)
                            hub.updateLastSeen();
                            logDebug("Timestamp updated for hub " + deviceName + " (RSSI changed)");
                        }
                        break;
                    }
                }
                
                if (!alreadyAdded) {
                    // Check if device should be hidden due to RSSI staleness
                    // For new devices, always add them (they start visible)
                    LegoHub newHub = new LegoHub(deviceName, deviceAddress, bleIndex);
                    newHub.updateRssi(deviceRssi); // Set initial RSSI
                    legoHubs.add(newHub);
                    logDebug("Added new LEGO hub: " + deviceName + " at address " + deviceAddress + " with RSSI " + deviceRssi);
                }
                
                return deviceAddress;
            } else {
                logDebug("Device is not a LEGO SPIKE Prime hub: " + deviceName);
                return null;
            }
            
        } catch (Exception e) {
            logDebug("Error checking device at index " + bleIndex + ": " + e);
            return null;
        }
    }
    
    /**
     * Test method to directly trigger the HubListChanged event
     * This is useful for testing the event chain without actual device discovery
     */
    @SimpleFunction(description = "Test method to directly trigger the HubListChanged event")
    public void TestTriggerHubListChanged() {
        logDebug("Directly triggering HubListChanged event with test data");
        HubListChanged("TestHub", "", "", "TestHub");
    }
    
    /**
     * Helper method to build a comma-separated string from a list of LegoHub objects
     * @param hubs List of LegoHub objects
     * @return Comma-separated string of hub names
     */
    private String buildHubListString(List<LegoHub> hubs) {
        if (hubs == null || hubs.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hubs.size(); i++) {
            LegoHub hub = hubs.get(i);
            sb.append(hub.getName());
            if (i < hubs.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
    
    /**
     * Helper method to build a comma-separated string of lost hubs
     * @param oldHubs Previous hub list
     * @param currentHubs Current hub list
     * @return Comma-separated string of lost hub names
     */
    private String buildLostHubsString(List<LegoHub> oldHubs, List<LegoHub> currentHubs) {
        if (oldHubs == null || oldHubs.isEmpty()) {
            return "";
        }
        
        List<LegoHub> lostHubs = new ArrayList<>();
        for (LegoHub oldHub : oldHubs) {
            boolean stillPresent = false;
            for (LegoHub currentHub : currentHubs) {
                if (oldHub.getAddress().equals(currentHub.getAddress())) {
                    stillPresent = true;
                    break;
                }
            }
            if (!stillPresent) {
                lostHubs.add(oldHub);
            }
        }
        
        return buildHubListString(lostHubs);
    }
    
    /**
     * Test method to directly trigger the HubConnected event
     * This is useful for testing the event chain without actual connection
     */
    @SimpleFunction(description = "Test method to directly trigger the HubConnected event")
    public void TestTriggerHubConnected() {
        logDebug("Directly triggering HubConnected event with test data");
        onConnected("Test LEGO Hub", "00:00:00:00:00:00");
    }
    
    /**
     * Test method to directly trigger the HubDisconnected event
     * This is useful for testing the event chain without actual disconnection
     */
    @SimpleFunction(description = "Test method to directly trigger the HubDisconnected event")
    public void TestTriggerHubDisconnected() {
        logDebug("Directly triggering HubDisconnected event");
        onDisconnected();
    }
    
    /**
     * Check if a device name indicates a LEGO SPIKE Prime hub
     * 
     * @param deviceName the name of the device
     * @return true if the device name indicates a LEGO SPIKE Prime hub
     */
    private boolean isLegoSpikeHub(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) {
            return false;
        }
        
        // First check if the device name matches the custom device name (case insensitive)
        if (deviceName.equalsIgnoreCase(customDeviceName)) {
            logDebug("Device matched custom device name: " + customDeviceName);
            return true;
        }
        
        // If no match with custom name, check for exact match with known LEGO hub names
        for (String hubName : LEGO_HUB_NAMES) {
            if (deviceName.equals(hubName)) {
                logDebug("Device matched known LEGO hub name: " + hubName);
                return true;
            }
        }
        
        // If no exact match, check for partial match with common LEGO identifiers
        String lowerName = deviceName.toLowerCase();
        if (lowerName.contains("lego") || 
            lowerName.contains("spike") || 
            lowerName.contains("hub")) {
            logDebug("Device name contains LEGO identifier: " + deviceName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Convert a port letter to a port number
     * 
     * @param portLetter the port letter (A-F)
     * @return the port number (0-5)
     */
    private int convertPortLetterToNumber(String portLetter) {
        switch (portLetter) {
            case "A": return 0;
            case "B": return 1;
            case "C": return 2;
            case "D": return 3;
            case "E": return 4;
            case "F": return 5;
            default: return 0;
        }
    }
    
    /**
     * Create a command to set the LED color
     * 
     * @param red the red component (0-255)
     * @param green the green component (0-255)
     * @param blue the blue component (0-255)
     * @return the command bytes
     */
    private byte[] createSetLEDCommand(int red, int green, int blue) {
        // Clamp RGB values to 0-255
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));
        
        // Create the command
        byte[] command = new byte[8];
        command[0] = 0x0A; // Port Output Command
        command[1] = 0x00; // Hub ID (always 0)
        command[2] = 0x32; // Port 50 (LED)
        command[3] = 0x00; // Startup and completion information
        command[4] = 0x01; // Subcommand: Set RGB
        command[5] = (byte) red;
        command[6] = (byte) green;
        command[7] = (byte) blue;
        
        return command;
    }
    
    /**
     * Create a command to set motor power
     * 
     * @param port the port number (0-5)
     * @param power the power level (-100 to 100)
     * @return the command bytes
     */
    private byte[] createMotorPowerCommand(int port, int power) {
        // Clamp power to -100 to 100
        power = Math.max(-100, Math.min(100, power));
        
        // Create the command
        byte[] command = new byte[6];
        command[0] = 0x0A; // Port Output Command
        command[1] = 0x00; // Hub ID (always 0)
        command[2] = (byte) port; // Port number
        command[3] = 0x00; // Startup and completion information
        command[4] = 0x01; // Subcommand: Set Power
        command[5] = (byte) power; // Power level
        
        return command;
    }
    
    /**
     * Called when a hub is connected
     * 
     * @param deviceName the name of the hub
     * @param deviceAddress the address of the hub
     */
    public void onConnected(String deviceName, String deviceAddress) {
        logDebug("onConnected called: " + deviceName + " (" + deviceAddress + ")");
        
        // Set connection state
        isConnected = true;
        connectedDeviceAddress = deviceAddress;
        connectedDeviceName = deviceName;
        
        // Trigger the HubConnected event
        HubConnected(deviceName, deviceAddress);
    }
    
    /**
     * Called when a hub is disconnected
     */
    public void onDisconnected() {
        logDebug("onDisconnected called");
        
        // Reset connection state
        isConnected = false;
        String deviceName = connectedDeviceName;
        String deviceAddress = connectedDeviceAddress;
        connectedDeviceName = "";
        connectedDeviceAddress = "";
        
        // Restart scanning if we were scanning before connection
        if (wasScanningBeforeConnection) {
            logDebug("Restarting scanning after disconnection");
            isScanning = true;        // Update scanning state
            startScanTimer();
            ScanningStarted();        // Trigger ScanningStarted event
            wasScanningBeforeConnection = false; // Reset flag
        }
        
        // Trigger the HubDisconnected event
        HubDisconnected();
    }


    
    /**
     * Event handler for BluetoothLE DeviceFound event
     * 
     * @param name the name of the device
     * @param address the address of the device
     * @param rssi the RSSI value of the device
     */
    public void BluetoothLE_DeviceFound(String name, String address, int rssi) {
        logDebug("BluetoothLE_DeviceFound: " + name + " (" + address + ") RSSI: " + rssi);
        
        // Check if this is a LEGO SPIKE Prime hub
        if (isLegoSpikeHub(name)) {
            logDebug("LEGO SPIKE Prime hub found via BLE event: " + name);
            
            // Check if the hub is already in our list
            boolean alreadyAdded = false;
            for (LegoHub hub : legoHubs) {
                if (hub.getAddress().equals(address)) {
                    alreadyAdded = true;
                    // Update timestamp for existing hub (this is the ONLY place timestamps should be updated)
                    hub.updateLastSeen();
                    logDebug("Updated timestamp for existing hub: " + name);
                    break;
                }
            }
            
            if (!alreadyAdded) {
                // Create a new hub object (timestamp is automatically set in constructor)
                LegoHub newHub = new LegoHub(name, address, -1); // We don't have the index here
                // Add the hub to our list
                legoHubs.add(newHub);
                logDebug("Added new hub via BLE event: " + name);
                // Note: HubChanged event will be triggered by CheckAllDevices method
            }
        }
    }
    
    /**
     * Event handler for BluetoothLE ScanningStateChanged event
     * 
     * @param scanning whether scanning is active
     */
    public void BluetoothLE_ScanningStateChanged(boolean scanning) {
        logDebug("BluetoothLE_ScanningStateChanged: " + scanning);
        
        // Update our scanning state
        isScanning = scanning;
        
        // Trigger the appropriate event
        if (scanning) {
            ScanningStarted();
        } else {
            ScanningStopped();
        }
    }
    
    /**
     * Event handler for BluetoothLE Connected event
     * 
     * @param address the address of the connected device
     */
    public void BluetoothLE_Connected(String address) {
        logDebug("BluetoothLE_Connected: " + address);
        
        // Find the hub in our list
        for (LegoHub hub : legoHubs) {
            if (hub.getAddress().equals(address)) {
                // Call our connection handler
                onConnected(hub.getName(), address);
                return;
            }
        }
        
        // If we get here, the connected device is not in our list
        // This can happen if the user connects directly through the BluetoothLE component
        logDebug("Connected to unknown device: " + address);
        onConnected("Unknown Hub", address);
    }
    
    /**
     * Event handler for BluetoothLE Disconnected event
     */
    public void BluetoothLE_Disconnected() {
        logDebug("BluetoothLE_Disconnected");
        
        // Call our disconnection handler
        onDisconnected();
    }
    
    /**
     * Event triggered when scanning for LEGO SPIKE Prime hubs starts
     */
    @SimpleEvent(description = "Event triggered when scanning for LEGO SPIKE Prime hubs starts")
    public void ScanningStarted() {
        logDebug("ScanningStarted event triggered");
        
        // Use Handler to dispatch event on main thread
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    EventDispatcher.dispatchEvent(LegoSpikePrime.this, "ScanningStarted");
                    logDebug("ScanningStarted event dispatched successfully");
                } catch (Exception e) {
                    logDebug("Error dispatching ScanningStarted event: " + e);
                    e.printStackTrace();
                }
            }
        });
    }
    
    /**
     * Event triggered when scanning for LEGO SPIKE Prime hubs stops
     */
    @SimpleEvent(description = "Event triggered when scanning for LEGO SPIKE Prime hubs stops")
    public void ScanningStopped() {
        logDebug("ScanningStopped event triggered");
        
        // Use Handler to dispatch event on main thread
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    EventDispatcher.dispatchEvent(LegoSpikePrime.this, "ScanningStopped");
                    logDebug("ScanningStopped event dispatched successfully");
                } catch (Exception e) {
                    logDebug("Error dispatching ScanningStopped event: " + e);
                    e.printStackTrace();
                }
            }
        });
    }
       /**
     * Event triggered when the list of detected LEGO SPIKE Prime hubs changes
     * 
     * @param newHubs comma-separated list of hub names that were newly detected
     * @param retainedHubs comma-separated list of hub names that remained from previous scan
     * @param lostHubs comma-separated list of hub names that were lost
     * @param allCurrentHubs comma-separated list of all currently detected hub names
     */
    @SimpleEvent(description = "Event triggered when the list of detected LEGO SPIKE Prime hubs changes")
    public void HubListChanged(String newHubs, String retainedHubs, String lostHubs, String allCurrentHubs) {
        logDebug("HubListChanged event triggered: new=[" + newHubs + "], retained=[" + retainedHubs + "], lost=[" + lostHubs + "], total=" + GetLegoHubCount());
        
        // Use Handler to dispatch event on main thread
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    EventDispatcher.dispatchEvent(LegoSpikePrime.this, "HubListChanged", newHubs, retainedHubs, lostHubs, allCurrentHubs);
                    logDebug("HubListChanged event dispatched successfully");
                } catch (Exception e) {
                    logDebug("Error dispatching HubListChanged event: " + e);
                    e.printStackTrace();
                }
            }
        });
    }
    
    /**
     * Event triggered when a LEGO SPIKE Prime hub is connected
     * 
     * @param deviceName the name of the hub
     * @param deviceAddress the address of the hub
     */
    @SimpleEvent(description = "Event triggered when a LEGO SPIKE Prime hub is connected")
    public void HubConnected(String deviceName, String deviceAddress) {
        logDebug("HubConnected event triggered: " + deviceName + " (" + deviceAddress + ")");
        
        try {
            EventDispatcher.dispatchEvent(this, "HubConnected", deviceName, deviceAddress);
            logDebug("HubConnected event dispatched successfully");
        } catch (Exception e) {
            logDebug("Error dispatching HubConnected event: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Event triggered when a LEGO SPIKE Prime hub is disconnected
     */
    @SimpleEvent(description = "Event triggered when a LEGO SPIKE Prime hub is disconnected")
    public void HubDisconnected() {
        logDebug("HubDisconnected event triggered");
        
        try {
            EventDispatcher.dispatchEvent(this, "HubDisconnected");
            logDebug("HubDisconnected event dispatched successfully");
        } catch (Exception e) {
            logDebug("Error dispatching HubDisconnected event: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Event triggered when an error occurs
     * 
     * @param errorMessage the error message
     */
    @SimpleEvent(description = "Event triggered when an error occurs")
    public void ErrorOccurred(String errorMessage) {
        logDebug("ErrorOccurred event triggered: " + errorMessage);
        
        try {
            EventDispatcher.dispatchEvent(this, "ErrorOccurred", errorMessage);
            logDebug("ErrorOccurred event dispatched successfully");
        } catch (Exception e) {
            logDebug("Error dispatching ErrorOccurred event: " + e);
            e.printStackTrace();
        }
    }
    
    // HELPER METHODS FOR RSSI STALENESS LOGIC
    
    /**
     * Get a list of visible hubs only
     * @return List of visible LegoHub objects
     */
    private List<LegoHub> getVisibleHubs() {
        List<LegoHub> visibleHubs = new ArrayList<>();
        for (LegoHub hub : legoHubs) {
            if (hub.isVisible()) {
                visibleHubs.add(hub);
            }
        }
        return visibleHubs;
    }
    
    /**
     * Helper method to get visible hubs from a specific list
     * 
     * @param hubs the list of hubs to filter
     * @return list of visible hubs only
     */
    private List<LegoHub> getVisibleHubsFromList(List<LegoHub> hubs) {
        List<LegoHub> visibleHubs = new ArrayList<>();
        if (hubs != null) {
            for (LegoHub hub : hubs) {
                if (hub.isVisible()) {
                    visibleHubs.add(hub);
                }
            }
        }
        return visibleHubs;
    }
    
    /**
     * Get the count of visible hubs
     * @return Number of visible hubs
     */
    private int getVisibleHubCount() {
        int count = 0;
        for (LegoHub hub : legoHubs) {
            if (hub.isVisible()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Build a comma-separated string from visible hubs only
     * @param hubs List of LegoHub objects (may include hidden hubs)
     * @return Comma-separated string of visible hub names
     */
    private String buildVisibleHubListString(List<LegoHub> hubs) {
        if (hubs == null || hubs.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (LegoHub hub : hubs) {
            if (hub.isVisible()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(hub.getName());
                first = false;
            }
        }
        return sb.toString();
    }
    
    /**
     * Build a comma-separated string of hubs that were visible before but not visible now
     * @param oldHubs Previous hub list
     * @param currentHubs Current hub list
     * @return Comma-separated string of lost visible hub names
     */
    private String buildLostVisibleHubsString(List<LegoHub> oldHubs, List<LegoHub> currentHubs) {
        List<LegoHub> lostHubs = new ArrayList<>();
        
        // Find hubs that were visible before
        for (LegoHub oldHub : oldHubs) {
            if (oldHub.isVisible()) {
                boolean stillVisible = false;
                // Check if still visible in current list
                for (LegoHub currentHub : currentHubs) {
                    if (oldHub.getAddress().equals(currentHub.getAddress()) && currentHub.isVisible()) {
                        stillVisible = true;
                        break;
                    }
                }
                if (!stillVisible) {
                    lostHubs.add(oldHub);
                }
            }
        }
        
        return buildHubListString(lostHubs);
    }
}

