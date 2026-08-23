package io.akka.focalboard.domain;

import java.util.List;
import java.util.Locale;

/**
 * Whether a card meets a filter (SPEC R2, R3, R6, R7, D1).
 *
 * <p>The condition names and their answers are the original's, enumerated over every state a
 * value can be in and recorded in `focalboard-port/docs/filter-conditions.md`. Three pairs
 * that look interchangeable are not: length decides {@code isEmpty}, truthiness decides
 * {@code isSet}, and the two disagree on an empty list.
 *
 * <p>The filter's own values are lower-cased before every comparison that takes a single
 * value, and the card's title is lower-cased before it is compared. A stored property value
 * is not: {@code includes} against a select option matches the option's id exactly as
 * stored.
 */
public final class CardFilter {

  public static final String TITLE = "title";

  private CardFilter() {}

  public static List<Card> apply(FilterGroup group, List<PropertyTemplate> templates,
                                 List<Card> cards) {
    return cards.stream().filter(card -> isGroupMet(group, templates, card)).toList();
  }

  public static boolean isGroupMet(FilterGroup group, List<PropertyTemplate> templates, Card card) {
    if (group == null || group.isEmpty()) {
      return true;
    }
    if (FilterGroup.OR.equals(group.operation())) {
      return group.clauses().stream().anyMatch(c -> isClauseMet(c, templates, card))
          || group.groups().stream().anyMatch(g -> isGroupMet(g, templates, card));
    }
    return group.clauses().stream().allMatch(c -> isClauseMet(c, templates, card))
        && group.groups().stream().allMatch(g -> isGroupMet(g, templates, card));
  }

  public static boolean isClauseMet(FilterClause clause, List<PropertyTemplate> templates,
                                    Card card) {
    var value = valueFor(clause, card);
    var values = clause.values();
    var first = values.isEmpty() ? null : values.get(0).toLowerCase(Locale.ROOT);

    return switch (clause.condition()) {
      case "includes" -> values.isEmpty()
          || values.stream().anyMatch(v -> value != null && value.equalsValue(v));
      case "notIncludes" -> values.isEmpty()
          || values.stream().noneMatch(v -> value != null && value.equalsValue(v));
      case "isEmpty" -> value == null || value.length() == 0;
      case "isNotEmpty" -> value != null && value.length() > 0;
      case "isSet" -> value != null && value.truthy();
      case "isNotSet" -> value == null || !value.truthy();
      case "is" -> first == null || first.equals(text(value));
      case "contains" -> first == null || containsValue(value, first);
      case "notContains" -> first == null || !containsValue(value, first);
      case "startsWith" -> first == null || text(value).startsWith(first);
      case "notStartsWith" -> first == null || !text(value).startsWith(first);
      case "endsWith" -> first == null || text(value).endsWith(first);
      case "notEndsWith" -> first == null || !text(value).endsWith(first);
      // The original answers false for these on anything that is not a date, and dates are
      // out of this slice's scope, so every card fails them rather than every card passing.
      case "isBefore", "isAfter" -> values.isEmpty();
      default -> true;
    };
  }

  /**
   * The value a clause reads. {@code title} is the card's own title, lower-cased, which is
   * why a filter on it compares against a lower-cased filter value on both sides. A clause
   * naming a property the board does not define still reads whatever the card stored under
   * that id (R7).
   */
  private static PropertyValue valueFor(FilterClause clause, Card card) {
    if (TITLE.equals(clause.propertyId())) {
      return PropertyValue.of(card.title().toLowerCase(Locale.ROOT));
    }
    return card.property(clause.propertyId());
  }

  private static String text(PropertyValue value) {
    // D1: a list value is joined rather than left to fail. The original throws here, and the
    // throw escapes the whole filter, so one clause the interface never offers takes the
    // view down with it.
    return value == null ? "" : value.asText();
  }

  private static boolean containsValue(PropertyValue value, String candidate) {
    return value != null && value.contains(candidate);
  }
}
