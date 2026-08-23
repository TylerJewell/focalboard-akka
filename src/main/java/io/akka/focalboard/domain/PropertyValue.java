package io.akka.focalboard.domain;

import java.util.List;

/**
 * What a card stores against one property: either one string or a list of them.
 *
 * <p>The two are kept apart rather than collapsed into a list, because four of the filter
 * conditions read them differently — {@code includes} is equality on a single value and
 * membership on a list, and an empty list is empty to {@code isEmpty} while being set to
 * {@code isSet}. A representation that could not tell an empty list from an empty string
 * would answer two of the specification's rules by accident.
 *
 * @param multi whether the value is a list; a single value keeps its string in {@code values}
 * @param values one entry for a single value, any number for a list
 */
public record PropertyValue(boolean multi, List<String> values) {

  public static PropertyValue of(String value) {
    return new PropertyValue(false, List.of(value));
  }

  public static PropertyValue ofList(List<String> values) {
    return new PropertyValue(true, List.copyOf(values));
  }

  public String single() {
    return multi || values.isEmpty() ? null : values.get(0);
  }

  /** The length the source's {@code isEmpty}/{@code isNotEmpty} measure. */
  public int length() {
    return multi ? values.size() : (values.isEmpty() ? 0 : values.get(0).length());
  }

  /** The truthiness the source's {@code isSet}/{@code isNotSet} read: an empty list is truthy. */
  public boolean truthy() {
    return multi || (!values.isEmpty() && !values.get(0).isEmpty());
  }

  /** The string the affix and substring conditions compare against. */
  public String asText() {
    return multi ? String.join("", values) : (values.isEmpty() ? "" : values.get(0));
  }

  public boolean contains(String candidate) {
    return multi ? values.contains(candidate) : asText().contains(candidate);
  }

  public boolean equalsValue(String candidate) {
    return multi ? values.contains(candidate) : candidate.equals(single());
  }
}
