package io.akka.focalboard.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SPEC R12–R15.
 *
 * <p>Every expected value came from firing a real drag-and-drop at focalboard's own Kanban
 * component and recording what it handed to `mutator`
 * (`focalboard-port/probes/source_probe/probe_move.test.tsx` →
 * `focalboard-port/probes/source_probe/move-answers.json`). The two functions being copied
 * are closures inside `kanban.tsx`, so running the component is the only way to see them
 * answer.
 */
public class CardMovementTest {

  static final String STATUS = "prop-status";

  static final PropertyTemplate GROUP_BY = new PropertyTemplate(STATUS, "Status", "select",
      List.of(new PropertyOption("opt-todo", "To Do", ""),
              new PropertyOption("opt-doing", "Doing", ""),
              new PropertyOption("opt-done", "Done", "")));

  static Card card(String id, String title, String option) {
    return new Card(id, title, 1L, 1L,
        option == null ? Map.of() : Map.of(STATUS, PropertyValue.of(option)));
  }

  static final Card C1 = card("card-1", "Alpha", "opt-todo");
  static final Card C2 = card("card-2", "Bravo", "opt-todo");
  static final Card C3 = card("card-3", "Charlie", "opt-doing");
  static final Card C4 = card("card-4", "Delta", "opt-done");

  static final List<String> DISPLAYED = List.of("card-1", "card-2", "card-3", "card-4");

  static ViewSpec view(List<String> cardOrder) {
    return ViewSpec.of("view-kanban", "By status", "board", STATUS,
        List.of(), FilterGroup.empty(), cardOrder, List.of(), List.of());
  }

  @Test
  public void dropOnColumnWritesTheGroupByProperty() {
    var move = CardMovement.dropOnColumn(view(DISPLAYED), GROUP_BY, C1,
        List.of("card-3"), "opt-doing");
    assertTrue(move.propertyChanged());
    assertEquals(STATUS, move.propertyId());
    assertEquals("opt-doing", move.newOptionId());
  }

  @Test
  public void dropOnTheCardsOwnColumnWritesNoProperty() {
    var move = CardMovement.dropOnColumn(view(DISPLAYED), GROUP_BY, C1,
        List.of("card-1", "card-2"), "opt-todo");
    assertFalse(move.propertyChanged(), "the value is already that option");
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4"), move.cardOrder(),
        "the order is still rewritten, moving the card after its column's last card");
  }

  @Test
  public void dropOnColumnInsertsAfterTheColumnsLastCard() {
    var move = CardMovement.dropOnColumn(view(DISPLAYED), GROUP_BY, C1,
        List.of("card-3"), "opt-doing");
    assertEquals(List.of("card-2", "card-3", "card-1", "card-4"), move.cardOrder());
  }

  @Test
  public void dropOnAnEmptyColumnLeavesTheOrderAlone() {
    var move = CardMovement.dropOnColumn(view(DISPLAYED), GROUP_BY, C1, List.of(), "opt-doing");
    assertTrue(move.propertyChanged());
    assertEquals(DISPLAYED, move.cardOrder(), "nothing to insert after, so nothing moves");
  }

  @Test
  public void aDraggedCardMissingFromTheOrderIsStillPlaced() {
    var move = CardMovement.dropOnColumn(view(List.of("card-2", "card-3", "card-4")),
        GROUP_BY, C1, List.of("card-3"), "opt-doing");
    assertEquals(List.of("card-2", "card-3", "card-1", "card-4"), move.cardOrder());
  }

  @Test
  public void insertsAtTheFrontWhenTheAnchorIsNotInTheOrder() {
    // R14. The target column's last card is card-3, which this order does not name, so the
    // index is -1 and the insert lands at the front of the whole order rather than at the
    // end of the target column.
    var move = CardMovement.dropOnColumn(view(List.of("card-1", "card-2", "card-4")),
        GROUP_BY, C1, List.of("card-3"), "opt-doing");
    assertEquals(List.of("card-1", "card-2", "card-4"), move.cardOrder());

    var fromEmpty = CardMovement.dropOnColumn(view(List.of()), GROUP_BY, C1,
        List.of("card-3"), "opt-doing");
    assertEquals(List.of("card-1"), fromEmpty.cardOrder());
  }

  @Test
  public void dropOnCardRebuildsFromTheDisplayedOrder() {
    // R15. This path ignores the view's stored cardOrder entirely, so a stored order that
    // disagrees with what is on screen has no effect on the result.
    var storedDisagrees = view(List.of("card-4", "card-3", "card-2", "card-1"));
    var move = CardMovement.dropOnCard(storedDisagrees, GROUP_BY, C1, C2, DISPLAYED);
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4"), move.cardOrder());
    assertFalse(move.propertyChanged(), "both cards are in To Do");
  }

  @Test
  public void dropOnCardDraggingUpLandsAtTheTargetsPosition() {
    var move = CardMovement.dropOnCard(view(DISPLAYED), GROUP_BY, C2, C1, DISPLAYED);
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4"), move.cardOrder());
    assertFalse(move.propertyChanged());
  }

  @Test
  public void dropOnCardInAnotherColumnWritesThePropertyToo() {
    var move = CardMovement.dropOnCard(view(DISPLAYED), GROUP_BY, C1, C3, DISPLAYED);
    assertTrue(move.propertyChanged());
    assertEquals("opt-doing", move.newOptionId());
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4"), move.cardOrder());
  }

  @Test
  public void dropOnACardInTheSameColumnDraggingDownLandsAfterIt() {
    // The +1 that only applies within one column: card-1 dragged down onto card-2 lands
    // after it, where the same drag onto a card in another column lands before it.
    var sameColumn = CardMovement.dropOnCard(view(DISPLAYED), GROUP_BY, C1, C2, DISPLAYED);
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4"), sameColumn.cardOrder());

    var otherColumn = CardMovement.dropOnCard(view(DISPLAYED), GROUP_BY, C1, C4, DISPLAYED);
    assertEquals(List.of("card-2", "card-3", "card-1", "card-4"), otherColumn.cardOrder(),
        "dragged down into another column, it lands at the target's index rather than after it");
  }

  @Test
  public void droppingACardOnItselfChangesNothing() {
    var move = CardMovement.dropOnCard(view(DISPLAYED), GROUP_BY, C1, C1, DISPLAYED);
    assertFalse(move.propertyChanged());
    assertEquals(DISPLAYED, move.cardOrder());
  }
}
