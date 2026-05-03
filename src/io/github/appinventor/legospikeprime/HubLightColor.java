package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Color options for LegoSpikeLight.SetCenterButtonLight.
 * Only includes colours confirmed present in the SPIKE Prime 3.x color module.
 * (CYAN and VIOLET are absent from the firmware's color module.)
 */
public enum HubLightColor implements OptionList<String> {
  Black("Black"),
  Red("Red"),
  Green("Green"),
  Yellow("Yellow"),
  Blue("Blue"),
  White("White"),
  Magenta("Magenta"),
  Orange("Orange"),
  Azure("Azure");

  private final String value;
  HubLightColor(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, HubLightColor> lookup = new HashMap<>();
  static { for (HubLightColor c : HubLightColor.values()) lookup.put(c.toUnderlyingValue(), c); }
  public static HubLightColor fromUnderlyingValue(String v) { return lookup.get(v); }
}
