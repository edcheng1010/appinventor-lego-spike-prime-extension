package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * LegoSpikeMotor — individual motor control blocks.
 * Matches LEGO SPIKE Prime "Motor Blocks Category".
 *
 * MVP blocks: StartMotor, StopMotor, SetMotorSpeed.
 *
 * Dependency: set the Connectivity property to a LegoSpikeConnectivity instance.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 2,
    description = "Controls individual motors on a LEGO SPIKE Prime hub. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMotor extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

    // Per-port speed storage: StartMotor uses the speed stored by SetMotorSpeed.
    private final Map<String, Integer> motorSpeeds = new HashMap<>();
    private static final int DEFAULT_SPEED = 50;

    public LegoSpikeMotor(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================================================
    // Connectivity property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":io.github.appinventor.legospikeprime.LegoSpikeConnectivity")
    public void Connectivity(Component component) {
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    public Component Connectivity() { return connectivity; }

    // =========================================================================
    // MVP blocks
    // =========================================================================

    /**
     * Start the motor on the given port running continuously.
     * Uses the speed set by SetMotorSpeed (default 50).
     *
     * @param port      port letter A–F
     * @param direction "clockwise" or "counterclockwise"
     */
    @SimpleFunction(description =
        "Start the motor on the given port (A-F) in the given direction "
        + "(\"clockwise\" or \"counterclockwise\"). Uses the speed set by SetMotorSpeed.")
    public void StartMotor(String port, String direction) {
        if (!checkConnected()) return;
        port = port.toUpperCase().trim();
        if (!isValidPort(port)) { reportError("Invalid port: " + port); return; }

        String dir = direction.toLowerCase().trim();
        String dirCode;
        if (dir.equals("clockwise") || dir.equals("cw")) {
            dirCode = "CW";
        } else if (dir.equals("counterclockwise") || dir.equals("ccw")) {
            dirCode = "CCW";
        } else {
            reportError("Invalid direction: " + direction
                + " — use \"clockwise\" or \"counterclockwise\"");
            return;
        }

        int speed = motorSpeeds.containsKey(port) ? motorSpeeds.get(port) : DEFAULT_SPEED;
        connectivity.sendCommand(String.format("MTR:%s:%s:%03d", port, dirCode, speed));
    }

    /**
     * Stop the motor on the given port.
     *
     * @param port port letter A–F
     */
    @SimpleFunction(description = "Stop the motor on the given port (A-F)")
    public void StopMotor(String port) {
        if (!checkConnected()) return;
        port = port.toUpperCase().trim();
        if (!isValidPort(port)) { reportError("Invalid port: " + port); return; }
        connectivity.sendCommand("MTR:" + port + ":STOP");
    }

    /**
     * Set the speed for a motor port (stored locally, applied on next StartMotor call).
     *
     * @param port  port letter A–F
     * @param speed 0–100 percent
     */
    @SimpleFunction(description =
        "Set the speed (0-100) for a motor port. Applied on the next StartMotor call.")
    public void SetMotorSpeed(String port, int speed) {
        port  = port.toUpperCase().trim();
        speed = Math.max(0, Math.min(100, speed));
        if (!isValidPort(port)) { reportError("Invalid port: " + port); return; }
        motorSpeeds.put(port, speed);
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private boolean checkConnected() {
        if (connectivity == null) {
            reportError("Connectivity not set"); return false;
        }
        if (!connectivity.IsConnected()) {
            reportError("Not connected to hub"); return false;
        }
        return true;
    }

    private static boolean isValidPort(String port) {
        return port.matches("[A-F]");
    }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
