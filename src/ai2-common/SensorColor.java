package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Color constants returned by the SPIKE Prime color sensor.
 * Display names use title case (first letter uppercase) to match the color
 * strings fired in the LegoSpikeSensors.ColorRead event.
 */
public enum SensorColor implements OptionList<String> {
  Black("Black"),
  Red("Red"),
  Green("Green"),
  Yellow("Yellow"),
  Blue("Blue"),
  White("White"),
  Cyan("Cyan"),
  Magenta("Magenta"),
  Orange("Orange"),
  Violet("Violet"),
  Azure("Azure"),
  None("None");

  private final String value;

  SensorColor(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, SensorColor> lookup = new HashMap<>();

  static {
    for (SensorColor c : SensorColor.values()) {
      lookup.put(c.toUnderlyingValue(), c);
    }
  }

  public static SensorColor fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
