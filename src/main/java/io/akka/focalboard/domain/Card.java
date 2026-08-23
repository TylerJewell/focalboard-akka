package io.akka.focalboard.domain;

import java.util.Map;

/**
 * A card, carrying only what deciding a view's answer depends on: its title, its two
 * timestamps and its property values. A card need not hold an entry for every property the
 * board defines, and a value it does hold need not name an option the property defines.
 */
public record Card(String id, String title, long createAt, long updateAt,
                   Map<String, PropertyValue> properties) {

  public PropertyValue property(String propertyId) {
    return properties.get(propertyId);
  }

  public Card withProperties(Map<String, PropertyValue> replacement, long at) {
    return new Card(id, title, createAt, at, Map.copyOf(replacement));
  }

  public Card withTitle(String newTitle, long at) {
    return new Card(id, newTitle, createAt, at, properties);
  }
}
