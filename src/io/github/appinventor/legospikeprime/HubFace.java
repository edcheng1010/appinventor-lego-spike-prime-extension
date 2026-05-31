package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Which face of the hub is 'up' — used by SetHubOrientation. */
public enum HubFace implements OptionList<String> {
  FaceUp("face_up"),
  FaceDown("face_down"),
  PortAUp("port_a_up"),
  PortADown("port_a_down"),
  PortEUp("port_e_up"),
  PortEDown("port_e_down");

  private final String value;
  HubFace(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, HubFace> lookup = new HashMap<>();
  static { for (HubFace f : HubFace.values()) lookup.put(f.toUnderlyingValue(), f); }
  public static HubFace fromUnderlyingValue(String v) { return lookup.get(v); }
}
