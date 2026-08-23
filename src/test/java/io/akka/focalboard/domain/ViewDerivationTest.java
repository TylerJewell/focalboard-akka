package io.akka.focalboard.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SPEC R1, R4, R4a, R5, R5a, R5b, R5c, R8–R11b, and open decision D7.
 *
 * <p>Every expected value here was taken from running the original, not from reading it:
 * `focalboard-port/probes/source_probe/probe_order.ts` puts these exact cases to
 * focalboard's own selector and records what it answers. Two of them contradicted what the
 * code appeared to say.
 */
public class ViewDerivationTest {

  static final String STATUS = "prop-status";
  static final String PRIORITY = "prop-priority";

  static final PropertyTemplate STATUS_TEMPLATE = new PropertyTemplate(STATUS, "Status", "select",
      List.of(new PropertyOption("opt-todo", "To Do", "propColorGray"),
              new PropertyOption("opt-doing", "Doing", "propColorYellow"),
              new PropertyOption("opt-done", "Done", "propColorGreen")));

  static final PropertyTemplate PRIORITY_TEMPLATE =
      new PropertyTemplate(PRIORITY, "Priority", "number", List.of());

  static final List<PropertyTemplate> PROPERTIES = List.of(STATUS_TEMPLATE, PRIORITY_TEMPLATE);

  static final long BASE = 1_700_000_000_000L;

  static Card card(String id, String title, int nth, String status, String priority) {
    var properties = new LinkedHashMap<String, PropertyValue>();
    if (status != null) {
      properties.put(STATUS, PropertyValue.of(status));
    }
    if (priority != null) {
      properties.put(PRIORITY, PropertyValue.of(priority));
    }
    return new Card(id, title, BASE + (nth * 1000L), BASE + (nth * 1000L), properties);
  }

  static final List<Card> CARDS = List.of(
      card("card-1", "Alpha", 0, "opt-todo", "3"),
      card("card-2", "Bravo", 1, "opt-doing", "1"),
      card("card-3", "Charlie", 2, "opt-done", "2"),
      card("card-4", "Delta", 3, "opt-todo", "2"),
      card("card-5", "Echo", 4, null, null));

  static final List<String> ALL_IN_ORDER =
      List.of("card-1", "card-2", "card-3", "card-4", "card-5");

  static ViewSpec view(List<SortOption> sorts, FilterGroup filter, List<String> cardOrder,
                       List<String> visible, List<String> hidden) {
    return ViewSpec.of("v", "v", "board", STATUS, sorts, filter, cardOrder, visible, hidden);
  }

  static ViewSpec plain() {
    return view(List.of(), FilterGroup.empty(), ALL_IN_ORDER, List.of(), List.of());
  }

  static List<String> optionIds(List<BoardGroup> groups) {
    return groups.stream().map(BoardGroup::optionId).toList();
  }

  static BoardGroup group(List<BoardGroup> groups, String optionId) {
    return groups.stream().filter(g -> g.optionId().equals(optionId)).findFirst().orElseThrow();
  }

  @Test
  public void derivesInTheOrderFilterSearchSortGroup() {
    var table = view(List.of(new SortOption(PRIORITY, false)),
        new FilterGroup("and",
            List.of(new FilterClause(STATUS, "notIncludes", List.of("opt-done"))), List.of()),
        List.of(), List.of(), List.of());

    var derived = ViewDerivation.derive(PROPERTIES, CARDS, table, "");

    assertEquals(List.of("card-2", "card-4", "card-1", "card-5"), derived.orderedCardIds());
    assertEquals(List.of("card-4", "card-1"), group(derived.visible(), "opt-todo").cardIds(),
        "a group carries the sorted order, so grouping follows sorting");
  }

  @Test
  public void aCompleteCardOrderIsHonouredExactly() {
    var reversed = view(List.of(), FilterGroup.empty(),
        List.of("card-5", "card-4", "card-3", "card-2", "card-1"), List.of(), List.of());
    assertEquals(List.of("card-5", "card-4", "card-3", "card-2", "card-1"),
        ViewDerivation.derive(PROPERTIES, CARDS, reversed, "").orderedCardIds());

    var rotated = view(List.of(), FilterGroup.empty(),
        List.of("card-3", "card-4", "card-5", "card-1", "card-2"), List.of(), List.of());
    assertEquals(List.of("card-3", "card-4", "card-5", "card-1", "card-2"),
        ViewDerivation.derive(PROPERTIES, CARDS, rotated, "").orderedCardIds());
  }

  @Test
  public void noCardOrderFallsBackToTitleThenCreateAt() {
    var none = view(List.of(), FilterGroup.empty(), List.of(), List.of(), List.of());
    assertEquals(ALL_IN_ORDER, ViewDerivation.derive(PROPERTIES, CARDS, none, "").orderedCardIds());

    var untitledEarly = new Card("card-x", "", BASE, BASE, Map.of());
    var untitledLate = new Card("card-y", "", BASE + 9000, BASE + 9000, Map.of());
    var alpha = card("card-1", "Alpha", 0, null, null);
    assertEquals(List.of("card-1", "card-x", "card-y"),
        ViewDerivation.derive(PROPERTIES, List.of(untitledLate, untitledEarly, alpha), none, "")
            .orderedCardIds(),
        "a titled card sorts above an untitled one; two untitled ones fall back to createAt");
  }

  @Test
  public void aPartialCardOrderPutsUnnamedCardsLast() {
    // D7. The original has no rule here — its comparator says "after" in both directions —
    // so this is the port's own, and the one place the two are expected to differ.
    var partial = view(List.of(), FilterGroup.empty(),
        List.of("card-4", "card-2"), List.of(), List.of());
    assertEquals(List.of("card-4", "card-2", "card-1", "card-3", "card-5"),
        ViewDerivation.derive(PROPERTIES, CARDS, partial, "").orderedCardIds());

    var namingAbsentCards = view(List.of(), FilterGroup.empty(),
        List.of("card-9", "card-8", "card-3"), List.of(), List.of());
    assertEquals(List.of("card-3", "card-1", "card-2", "card-4", "card-5"),
        ViewDerivation.derive(PROPERTIES, CARDS, namingAbsentCards, "").orderedCardIds(),
        "ids the board does not have take no position and hold none open");
  }

  @Test
  public void theLastSortOptionDecidesTheOrder() {
    var byStatusThenPriority = view(
        List.of(new SortOption(STATUS, false), new SortOption(PRIORITY, false)),
        FilterGroup.empty(), List.of(), List.of(), List.of());
    assertEquals(List.of("card-2", "card-3", "card-4", "card-1", "card-5"),
        ViewDerivation.derive(PROPERTIES, CARDS, byStatusThenPriority, "").orderedCardIds(),
        "the priority order wins; status survives only where priority ties");
  }

  @Test
  public void sortByPriorityBreaksTiesByTitle() {
    var byPriority = view(List.of(new SortOption(PRIORITY, false)),
        FilterGroup.empty(), List.of(), List.of(), List.of());
    assertEquals(List.of("card-2", "card-3", "card-4", "card-1", "card-5"),
        ViewDerivation.derive(PROPERTIES, CARDS, byPriority, "").orderedCardIds(),
        "Charlie and Delta both hold 2, so the tie falls back to title");
  }

  @Test
  public void emptyValuesSortLastEvenReversed() {
    var reversed = view(List.of(new SortOption(PRIORITY, true)),
        FilterGroup.empty(), List.of(), List.of(), List.of());
    assertEquals(List.of("card-1", "card-4", "card-3", "card-2", "card-5"),
        ViewDerivation.derive(PROPERTIES, CARDS, reversed, "").orderedCardIds(),
        "card-5 has no priority and stays last, while everything else inverts");

    // The one empty-valued card above is already last in the input, so a comparator that
    // applied the reversal to the emptiness test could still land it in the same place. These
    // four put the empty ones first.
    var papa = new Card("card-p", "Papa", BASE, BASE, Map.of());
    var quebec = new Card("card-q", "Quebec", BASE + 1000, BASE + 1000, Map.of());
    var romeo = new Card("card-r", "Romeo", BASE + 2000, BASE + 2000,
        Map.of(PRIORITY, PropertyValue.of("2")));
    var sierra = new Card("card-s", "Sierra", BASE + 3000, BASE + 3000,
        Map.of(PRIORITY, PropertyValue.of("1")));
    var emptiesFirst = List.of(papa, quebec, romeo, sierra);

    assertEquals(List.of("card-s", "card-r", "card-p", "card-q"),
        ViewDerivation.derive(PROPERTIES, emptiesFirst,
            view(List.of(new SortOption(PRIORITY, false)), FilterGroup.empty(),
                List.of(), List.of(), List.of()), "").orderedCardIds());
    assertEquals(List.of("card-r", "card-s", "card-p", "card-q"),
        ViewDerivation.derive(PROPERTIES, emptiesFirst,
            view(List.of(new SortOption(PRIORITY, true)), FilterGroup.empty(),
                List.of(), List.of(), List.of()), "").orderedCardIds(),
        "reversing swaps Romeo and Sierra and leaves the two empty ones at the back");
  }

  @Test
  public void reversingInvertsTheTieBreakAsWell() {
    var byTitle = view(List.of(new SortOption(SortOption.TITLE, false)),
        FilterGroup.empty(), List.of(), List.of(), List.of());
    assertEquals(ALL_IN_ORDER,
        ViewDerivation.derive(PROPERTIES, CARDS, byTitle, "").orderedCardIds());

    var byTitleReversed = view(List.of(new SortOption(SortOption.TITLE, true)),
        FilterGroup.empty(), List.of(), List.of(), List.of());
    assertEquals(List.of("card-5", "card-4", "card-3", "card-2", "card-1"),
        ViewDerivation.derive(PROPERTIES, CARDS, byTitleReversed, "").orderedCardIds());
  }

  @Test
  public void selectPropertiesSortByTheirDisplayValue() {
    var byStatus = view(List.of(new SortOption(STATUS, false)),
        FilterGroup.empty(), List.of(), List.of(), List.of());
    assertEquals(List.of("card-2", "card-3", "card-1", "card-4", "card-5"),
        ViewDerivation.derive(PROPERTIES, CARDS, byStatus, "").orderedCardIds(),
        "Doing, Done, To Do — the labels' order, not the ids'");

    // Status's ids and labels happen to sort the same way, so the assertion above holds for
    // an implementation comparing either. This property's do not: the labels run A to Z while
    // the ids run Z to A.
    var rank = new PropertyTemplate("prop-rank", "Rank", "select",
        List.of(new PropertyOption("zzz-option", "Alpha rank", ""),
                new PropertyOption("aaa-option", "Zulu rank", "")));
    var able = new Card("card-a", "Able", BASE, BASE,
        Map.of("prop-rank", PropertyValue.of("zzz-option")));
    var baker = new Card("card-b", "Baker", BASE + 1000, BASE + 1000,
        Map.of("prop-rank", PropertyValue.of("aaa-option")));
    var byRank = new ViewSpec("v", "v", "board", "prop-rank",
        List.of(new SortOption("prop-rank", false)), FilterGroup.empty(),
        List.of(), List.of(), List.of(), List.of());
    assertEquals(List.of("card-a", "card-b"),
        ViewDerivation.derive(List.of(rank), List.of(able, baker), byRank, "").orderedCardIds(),
        "Alpha rank before Zulu rank; comparing the ids would put them the other way round");
  }

  @Test
  public void columnsAreVisibleThenUnassignedWithEmptyFirst() {
    assertEquals(List.of("", "opt-todo", "opt-doing", "opt-done"),
        optionIds(ViewDerivation.derive(PROPERTIES, CARDS, plain(), "").visible()));

    var pinned = view(List.of(), FilterGroup.empty(), List.of(),
        List.of("opt-done", "opt-doing", "opt-todo"), List.of());
    assertEquals(List.of("", "opt-done", "opt-doing", "opt-todo"),
        optionIds(ViewDerivation.derive(PROPERTIES, CARDS, pinned, "").visible()));

    var emptyPinnedSecond = view(List.of(), FilterGroup.empty(), List.of(),
        List.of("opt-todo", ""), List.of());
    assertEquals(List.of("opt-todo", "", "opt-doing", "opt-done"),
        optionIds(ViewDerivation.derive(PROPERTIES, CARDS, emptyPinnedSecond, "").visible()),
        "naming the empty column moves it rather than adding a second one");
  }

  @Test
  public void anOptionWithNoCardsStillGetsAColumn() {
    var filtered = view(List.of(), new FilterGroup("and",
        List.of(new FilterClause(STATUS, "notIncludes", List.of("opt-done"))), List.of()),
        List.of(), List.of(), List.of());
    var derived = ViewDerivation.derive(PROPERTIES, CARDS, filtered, "");
    assertTrue(group(derived.visible(), "opt-done").cardIds().isEmpty(),
        "columns come from the property's options, not from the cards");
  }

  @Test
  public void absentAndUnknownValuesLandInTheEmptyColumn() {
    var stray = card("card-6", "Foxtrot", 5, "opt-that-was-deleted", null);
    var cards = new ArrayList<>(CARDS);
    cards.add(stray);
    var withStray = view(List.of(), FilterGroup.empty(),
        List.of("card-1", "card-2", "card-3", "card-4", "card-5", "card-6"),
        List.of(), List.of());
    var derived = ViewDerivation.derive(PROPERTIES, cards, withStray, "");
    assertEquals(List.of("card-5", "card-6"), group(derived.visible(), "").cardIds());
    assertEquals("No Status", group(derived.visible(), "").label());
  }

  @Test
  public void hiddenOptionsAreReportedSeparately() {
    var hiding = view(List.of(), FilterGroup.empty(), ALL_IN_ORDER, List.of(), List.of("opt-done"));
    var derived = ViewDerivation.derive(PROPERTIES, CARDS, hiding, "");
    assertEquals(List.of("", "opt-todo", "opt-doing"), optionIds(derived.visible()));
    assertEquals(List.of("opt-done"), optionIds(derived.hidden()));
    assertEquals(List.of("card-3"), group(derived.hidden(), "opt-done").cardIds());
  }

  @Test
  public void theEmptyColumnItselfCanBeHidden() {
    var hidingEmpty = view(List.of(), FilterGroup.empty(), ALL_IN_ORDER, List.of(), List.of(""));
    var derived = ViewDerivation.derive(PROPERTIES, CARDS, hidingEmpty, "");
    assertEquals(List.of("opt-todo", "opt-doing", "opt-done"), optionIds(derived.visible()));
    assertEquals(List.of(""), optionIds(derived.hidden()));
    assertEquals(List.of("card-5"), group(derived.hidden(), "").cardIds());
  }

  @Test
  public void aViewWithNoGroupByReturnsOneEmptyColumn() {
    var ungrouped = ViewSpec.of("v", "v", "table", null, List.of(), FilterGroup.empty(),
        ALL_IN_ORDER, List.of(), List.of());
    var derived = ViewDerivation.derive(PROPERTIES, CARDS, ungrouped, "");
    assertEquals(List.of(""), optionIds(derived.visible()));
    assertEquals(ALL_IN_ORDER, group(derived.visible(), "").cardIds());
    assertTrue(derived.hidden().isEmpty());
  }

  @Test
  public void searchMatchesTitlesAndDisplayedPropertyValues() {
    assertEquals(List.of("card-1"),
        ViewDerivation.derive(PROPERTIES, CARDS, plain(), "al").orderedCardIds());
    assertEquals(List.of("card-2"),
        ViewDerivation.derive(PROPERTIES, CARDS, plain(), "doing").orderedCardIds(),
        "the search reads the option's label, though the card stores opt-doing");
    assertTrue(group(ViewDerivation.derive(PROPERTIES, CARDS, plain(), "al").visible(), "opt-doing")
        .cardIds().isEmpty());
  }
}
