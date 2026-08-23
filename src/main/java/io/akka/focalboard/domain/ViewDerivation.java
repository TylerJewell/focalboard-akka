package io.akka.focalboard.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What one view answers about one board's cards (SPEC R1, R4–R5c, R8–R11b, D7).
 *
 * <p>Filter, then search, then sort, then group. The order is the argument: a group carries
 * its cards in the sorted order, so sorting before grouping is what makes a column's
 * contents readable rather than arbitrary.
 */
public final class ViewDerivation {

  private ViewDerivation() {}

  public static DerivedView derive(List<PropertyTemplate> properties, List<Card> cards,
                                   ViewSpec view, String searchText) {
    var kept = CardFilter.apply(view.filter(), properties, cards);
    kept = search(kept, properties, searchText);
    var ordered = sort(kept, properties, view);
    var orderedIds = ordered.stream().map(Card::id).toList();

    var groupBy = template(properties, view.groupById());
    var columns = columnOrder(view, groupBy);
    var visible = new ArrayList<BoardGroup>();
    var hidden = new ArrayList<BoardGroup>();
    for (var optionId : columns) {
      var column = column(optionId, groupBy, view, ordered);
      if (view.hiddenOptionIds().contains(optionId)) {
        hidden.add(column);
      } else {
        visible.add(column);
      }
    }
    return new DerivedView(view.id(), orderedIds, List.copyOf(visible), List.copyOf(hidden));
  }

  // ---------------------------------------------------------------- searching

  /**
   * R11b. A card matches when its title contains the text, or when a property's *displayed*
   * form does. A select shows its option's label, so searching for the label finds a card
   * that stores only the id.
   *
   * <p>A multi-select is the one that is not a substring test: the original collects the
   * option labels and asks whether the search text is one of them, so a partial label
   * matches a select and not a multi-select.
   */
  private static List<Card> search(List<Card> cards, List<PropertyTemplate> properties,
                                   String searchText) {
    if (searchText == null || searchText.isEmpty()) {
      return cards;
    }
    var needle = searchText.toLowerCase(Locale.ROOT);
    return cards.stream().filter(card -> {
      if (card.title().toLowerCase(Locale.ROOT).contains(needle)) {
        return true;
      }
      for (var entry : card.properties().entrySet()) {
        var template = template(properties, entry.getKey());
        var value = entry.getValue();
        if (template == null || value.length() == 0) {
          continue;
        }
        if ("select".equals(template.type())) {
          var label = template.option(value.asText()).map(PropertyOption::value).orElse(null);
          if (label != null && label.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
          }
        } else if ("multiSelect".equals(template.type())) {
          var labels = value.values().stream()
              .map(id -> template.option(id).map(PropertyOption::value).orElse(null))
              .filter(java.util.Objects::nonNull)
              .map(l -> l.toLowerCase(Locale.ROOT))
              .toList();
          if (labels.contains(needle)) {
            return true;
          }
        } else if (value.asText().toLowerCase(Locale.ROOT).contains(needle)) {
          return true;
        }
      }
      return false;
    }).toList();
  }

  // ----------------------------------------------------------------- ordering

  private static List<Card> sort(List<Card> cards, List<PropertyTemplate> properties,
                                 ViewSpec view) {
    if (view.sortOptions().isEmpty()) {
      return manualOrder(cards, view.cardOrder());
    }
    // R5: each option re-sorts the whole list, so the last one decides and the earlier ones
    // survive only through the stability of the sort.
    var sorted = new ArrayList<>(cards);
    for (var option : view.sortOptions()) {
      sorted.sort(comparator(option, properties));
    }
    return List.copyOf(sorted);
  }

  /**
   * R4, R4a and D7. Cards the order names take their named positions; the rest follow, by
   * title then create time.
   *
   * <p>The original's comparator answers "after" in both directions when exactly one of two
   * cards is named, which is not an order, so this is the port's own rule rather than a copy.
   * On a complete order — which is the only kind the interface itself writes — the two agree.
   */
  private static List<Card> manualOrder(List<Card> cards, List<String> cardOrder) {
    var placed = new ArrayList<Card>();
    var unplaced = new ArrayList<Card>();
    var byId = new LinkedHashMap<String, Card>();
    for (var card : cards) {
      byId.put(card.id(), card);
    }
    for (var id : cardOrder) {
      var card = byId.remove(id);
      if (card != null) {
        placed.add(card);
      }
    }
    unplaced.addAll(byId.values());
    unplaced.sort(ViewDerivation::titleThenCreateAt);
    placed.addAll(unplaced);
    return List.copyOf(placed);
  }

  /** R4: a titled card above an untitled one, then title, then create time. */
  static int titleThenCreateAt(Card a, Card b) {
    var left = a.title();
    var right = b.title();
    if (!left.isEmpty() && !right.isEmpty()) {
      var byTitle = left.compareToIgnoreCase(right);
      if (byTitle != 0) {
        return byTitle;
      }
      return Long.compare(a.createAt(), b.createAt());
    }
    if (!left.isEmpty()) {
      return -1;
    }
    if (!right.isEmpty()) {
      return 1;
    }
    return Long.compare(a.createAt(), b.createAt());
  }

  private static Comparator<Card> comparator(SortOption option, List<PropertyTemplate> properties) {
    if (SortOption.TITLE.equals(option.propertyId())) {
      return (a, b) -> {
        var result = titleThenCreateAt(a, b);
        return option.reversed() ? -result : result;
      };
    }
    var template = template(properties, option.propertyId());
    if (template == null) {
      return (a, b) -> 0;
    }
    return (a, b) -> compareByProperty(a, b, template, option.reversed());
  }

  private static int compareByProperty(Card a, Card b, PropertyTemplate template,
                                       boolean reversed) {
    if ("createdTime".equals(template.type())) {
      return signed(Long.compare(a.createAt(), b.createAt()), a, b, reversed);
    }
    if ("updatedTime".equals(template.type())) {
      return signed(Long.compare(a.updateAt(), b.updateAt()), a, b, reversed);
    }

    var left = displayValue(a, template);
    var right = displayValue(b, template);

    // R5a. The emptiness test is settled before the reversal, so a card with no value sorts
    // last whether the option is reversed or not.
    if (!left.isEmpty() && right.isEmpty()) {
      return -1;
    }
    if (left.isEmpty() && !right.isEmpty()) {
      return 1;
    }
    if (left.isEmpty()) {
      return titleThenCreateAt(a, b);
    }

    int result;
    if ("number".equals(template.type())) {
      result = Double.compare(asNumber(left), asNumber(right));
    } else {
      result = left.compareToIgnoreCase(right);
    }
    return signed(result, a, b, reversed);
  }

  private static int signed(int result, Card a, Card b, boolean reversed) {
    if (result == 0) {
      result = titleThenCreateAt(a, b);
    }
    return reversed ? -result : result;
  }

  /** R5b: a select or multi-select is compared by the label it shows, not the id it stores. */
  private static String displayValue(Card card, PropertyTemplate template) {
    var value = card.property(template.id());
    if (value == null || value.length() == 0) {
      return "";
    }
    if ("select".equals(template.type()) || "multiSelect".equals(template.type())) {
      var firstId = value.multi() ? value.values().get(0) : value.asText();
      return template.option(firstId).map(PropertyOption::value).orElse("");
    }
    return value.asText();
  }

  private static double asNumber(String text) {
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  // ----------------------------------------------------------------- grouping

  /**
   * R8. The empty column first, then the pinned ones in the order given, then every option
   * the view pins neither way, in the property's own option order. Naming the empty column
   * in either list moves it out of the leading position rather than adding a second one.
   */
  private static List<String> columnOrder(ViewSpec view, PropertyTemplate groupBy) {
    var visible = view.visibleOptionIds();
    var hidden = view.hiddenOptionIds();
    var order = new ArrayList<String>();
    if (!visible.contains("") && !hidden.contains("")) {
      order.add("");
    }
    order.addAll(visible);
    order.addAll(hidden);
    if (groupBy != null) {
      for (var option : groupBy.options()) {
        if (!visible.contains(option.id()) && !hidden.contains(option.id())) {
          order.add(option.id());
        }
      }
    }
    return order;
  }

  /** R9, R10, R11a. A column exists because the property offers the option, not because a card fell in it. */
  private static BoardGroup column(String optionId, PropertyTemplate groupBy, ViewSpec view,
                                   List<Card> ordered) {
    if (optionId.isEmpty()) {
      var label = groupBy == null ? "No " : "No " + groupBy.name();
      var cards = ordered.stream()
          .filter(card -> groupValue(card, groupBy).isEmpty())
          .map(Card::id).toList();
      return new BoardGroup("", label, "", cards);
    }
    var option = groupBy == null ? Optional.<PropertyOption>empty() : groupBy.option(optionId);
    var cards = ordered.stream()
        .filter(card -> optionId.equals(groupValue(card, groupBy).orElse(null)))
        .map(Card::id).toList();
    return new BoardGroup(optionId,
        option.map(PropertyOption::value).orElse(optionId),
        option.map(PropertyOption::color).orElse(""),
        cards);
  }

  /**
   * R10. A card falls outside every named column when it stores nothing for the group-by
   * property, or stores an option the property no longer defines.
   */
  private static Optional<String> groupValue(Card card, PropertyTemplate groupBy) {
    if (groupBy == null) {
      return Optional.empty();
    }
    var value = card.property(groupBy.id());
    if (value == null || value.length() == 0) {
      return Optional.empty();
    }
    var id = value.multi() ? value.values().get(0) : value.asText();
    return groupBy.defines(id) ? Optional.of(id) : Optional.empty();
  }

  static PropertyTemplate template(List<PropertyTemplate> properties, String propertyId) {
    if (propertyId == null) {
      return null;
    }
    for (var template : properties) {
      if (template.id().equals(propertyId)) {
        return template;
      }
    }
    return null;
  }
}
