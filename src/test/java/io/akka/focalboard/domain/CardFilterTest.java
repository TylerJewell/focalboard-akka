package io.akka.focalboard.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC R2, R3, R6, R7 and open decision D1. */
public class CardFilterTest {

  private static final PropertyTemplate SELECT = new PropertyTemplate(
      "p-select", "Status", "select",
      List.of(new PropertyOption("opt-todo", "To Do", ""),
              new PropertyOption("opt-done", "Done", "")));

  private static final PropertyTemplate MULTI = new PropertyTemplate(
      "p-multi", "Tags", "multiSelect",
      List.of(new PropertyOption("tag-a", "A", ""), new PropertyOption("tag-b", "B", "")));

  private static final List<PropertyTemplate> TEMPLATES = List.of(SELECT, MULTI);

  private static Card card(Map<String, PropertyValue> properties) {
    return new Card("card-1", "Alpha", 1_700_000_000_000L, 1_700_000_000_000L, properties);
  }

  private static FilterClause clause(String propertyId, String condition, String... values) {
    return new FilterClause(propertyId, condition, List.of(values));
  }

  private static FilterGroup group(String operation, FilterClause... clauses) {
    return new FilterGroup(operation, List.of(clauses), List.of());
  }

  @Test
  public void groupOperatorsAndNesting() {
    var todo = card(Map.of("p-select", PropertyValue.of("opt-todo")));
    var isTodo = clause("p-select", "includes", "opt-todo");
    var isDone = clause("p-select", "includes", "opt-done");

    assertTrue(CardFilter.isGroupMet(FilterGroup.empty(), TEMPLATES, todo), "an empty group is met");
    assertTrue(CardFilter.isGroupMet(group("and", isTodo, isTodo), TEMPLATES, todo));
    assertFalse(CardFilter.isGroupMet(group("and", isTodo, isDone), TEMPLATES, todo));
    assertTrue(CardFilter.isGroupMet(group("or", isTodo, isDone), TEMPLATES, todo));
    assertFalse(CardFilter.isGroupMet(group("or", isDone, isDone), TEMPLATES, todo));

    var nested = new FilterGroup("or", List.of(isDone), List.of(group("and", isTodo, isTodo)));
    assertTrue(CardFilter.isGroupMet(nested, TEMPLATES, todo), "a group inside a group is evaluated");
  }

  @Test
  public void aClauseWithNoValuesIsMet() {
    var todo = card(Map.of("p-select", PropertyValue.of("opt-todo")));
    for (var condition : List.of("includes", "notIncludes", "is", "contains", "notContains",
        "startsWith", "notStartsWith", "endsWith", "notEndsWith", "isBefore", "isAfter")) {
      assertTrue(CardFilter.isClauseMet(clause("p-select", condition), TEMPLATES, todo),
          condition + " with no values must be met");
    }
  }

  @Test
  public void aClauseOnAnUndefinedPropertyReadsTheRawValue() {
    var withUnknown = card(Map.of("p-nowhere", PropertyValue.of("something")));
    assertTrue(CardFilter.isClauseMet(clause("p-nowhere", "includes", "something"),
        TEMPLATES, withUnknown));
    assertFalse(CardFilter.isClauseMet(clause("p-nowhere", "includes", "other"),
        TEMPLATES, withUnknown));
    assertTrue(CardFilter.isClauseMet(clause("p-nowhere", "isSet"), TEMPLATES, withUnknown));
  }

  @Test
  public void isSetAndIsNotEmptyDisagreeOnAnEmptyList() {
    var emptyList = card(Map.of("p-multi", PropertyValue.ofList(List.of())));
    assertTrue(CardFilter.isClauseMet(clause("p-multi", "isSet"), TEMPLATES, emptyList),
        "an empty list is set");
    assertFalse(CardFilter.isClauseMet(clause("p-multi", "isNotEmpty"), TEMPLATES, emptyList),
        "an empty list is empty");
    assertTrue(CardFilter.isClauseMet(clause("p-multi", "isEmpty"), TEMPLATES, emptyList));
  }

  @Test
  public void includesIsMembershipOnAListAndEqualityOnASingleValue() {
    var list = card(Map.of("p-multi", PropertyValue.ofList(List.of("tag-a", "tag-b"))));
    var single = card(Map.of("p-select", PropertyValue.of("opt-todo")));
    assertTrue(CardFilter.isClauseMet(clause("p-multi", "includes", "tag-b"), TEMPLATES, list));
    assertFalse(CardFilter.isClauseMet(clause("p-multi", "includes", "tag-c"), TEMPLATES, list));
    assertTrue(CardFilter.isClauseMet(clause("p-select", "includes", "opt-todo"), TEMPLATES, single));
    assertTrue(CardFilter.isClauseMet(clause("p-select", "notIncludes", "opt-done"), TEMPLATES, single));
  }

  @Test
  public void affixConditionsJoinAListValue() {
    // D1. The original throws here; this port joins the list and applies the condition, so
    // one unusable clause does not take the whole view down.
    var list = card(Map.of("p-multi", PropertyValue.ofList(List.of("tag-a", "tag-b"))));
    assertTrue(CardFilter.isClauseMet(clause("p-multi", "startsWith", "tag-a"), TEMPLATES, list));
    assertTrue(CardFilter.isClauseMet(clause("p-multi", "endsWith", "tag-b"), TEMPLATES, list));
    assertFalse(CardFilter.isClauseMet(clause("p-multi", "startsWith", "tag-b"), TEMPLATES, list));
    assertTrue(CardFilter.isClauseMet(clause("p-multi", "notStartsWith", "tag-b"), TEMPLATES, list));
  }

  @Test
  public void theTitlePseudoPropertyIsMatchedLowerCased() {
    var titled = new Card("card-1", "Alpha Beta", 1L, 1L, Map.of());
    assertTrue(CardFilter.isClauseMet(clause("title", "contains", "alpha"), TEMPLATES, titled));
    assertTrue(CardFilter.isClauseMet(clause("title", "contains", "Alpha"), TEMPLATES, titled),
        "the filter's own value is lower-cased before the comparison");
    assertTrue(CardFilter.isClauseMet(clause("title", "is", "alpha beta"), TEMPLATES, titled));
  }

  @Test
  public void appliesAGroupOverACollection() {
    var todo = card(Map.of("p-select", PropertyValue.of("opt-todo")));
    var done = new Card("card-2", "Bravo", 1L, 1L,
        Map.of("p-select", PropertyValue.of("opt-done")));
    var kept = CardFilter.apply(group("and", clause("p-select", "notIncludes", "opt-done")),
        TEMPLATES, List.of(todo, done));
    assertTrue(kept.stream().map(Card::id).toList().equals(List.of("card-1")));
  }
}
