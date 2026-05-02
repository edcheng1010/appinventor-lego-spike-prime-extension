package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Hub port options A–F used by motor and sensor components. */
public enum Port implements OptionList<String> {
  A("A"),
  B("B"),
  C("C"),
  D("D"),
  E("E"),
  F("F");

  private final String value;

  Port(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, Port> lookup = new HashMap<>();

  static {
    for (Port p : Port.values()) {
      lookup.put(p.toUnderlyingValue(), p);
    }
  }

  public static Port fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
