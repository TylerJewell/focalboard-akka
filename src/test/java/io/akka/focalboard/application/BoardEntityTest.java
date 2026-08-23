package io.akka.focalboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.focalboard.domain.Card;
import io.akka.focalboard.domain.DerivedView;
import io.akka.focalboard.domain.FilterGroup;
import io.akka.focalboard.domain.PropertyOption;
import io.akka.focalboard.domain.PropertyTemplate;
import io.akka.focalboard.domain.PropertyValue;
import io.akka.focalboard.domain.SortOption;
import io.akka.focalboard.domain.ViewSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC R16, R17, R18, R19 and D3. */
public class BoardEntityTest {

  static final String STATUS = "prop-status";
  static final String PRIORITY = "prop-priority";
  static final long T0 = 1_700_000_000_000L;

  static final PropertyTemplate STATUS_TEMPLATE = new PropertyTemplate(STATUS, "Status", "select",
      List.of(new PropertyOption("opt-todo", "To Do", ""),
              new PropertyOption("opt-doing", "Doing", ""),
              new PropertyOption("opt-done", "Done", "")));

  static final PropertyTemplate PRIORITY_TEMPLATE =
      new PropertyTemplate(PRIORITY, "Priority", "number", List.of());

  static Card card(String id, String title, int nth, String status, String priority) {
    var properties = new LinkedHashMap<String, PropertyValue>();
    if (status != null) {
      properties.put(STATUS, PropertyValue.of(status));
    }
    if (priority != null) {
      properties.put(PRIORITY, PropertyValue.of(priority));
    }
    return new Card(id, title, T0 + (nth * 1000L), T0 + (nth * 1000L), properties);
  }

  static ViewSpec view(String id, List<String> cardOrder) {
    return ViewSpec.of(id, id, "board", STATUS, List.of(), FilterGroup.empty(),
        cardOrder, List.of(), List.of());
  }

  static EventSourcedTestKit<BoardState, BoardEvent, BoardEntity> seeded() {
    var kit = EventSourcedTestKit.of("board-1", BoardEntity::new);
    kit.method(BoardEntity::create).invoke(new BoardEntity.CreateBoard(
        "team-1", "Port board", List.of(STATUS_TEMPLATE, PRIORITY_TEMPLATE), T0));
    kit.method(BoardEntity::addCards).invoke(new BoardEntity.AddCards(List.of(
        card("card-1", "Alpha", 0, "opt-todo", "3"),
        card("card-2", "Bravo", 1, "opt-doing", "1"),
        card("card-3", "Charlie", 2, "opt-done", "2"),
        card("card-4", "Delta", 3, "opt-todo", "2"),
        card("card-5", "Echo", 4, null, null))));
    kit.method(BoardEntity::addViews).invoke(new BoardEntity.AddViews(List.of(
        view("view-kanban", List.of("card-1", "card-2", "card-3", "card-4", "card-5")),
        view("view-table", List.of("card-5", "card-4", "card-3", "card-2", "card-1")))));
    return kit;
  }

  @Test
  public void patchReplacesNamedFieldsAndLeavesOthers() {
    var kit = seeded();
    kit.method(BoardEntity::replaceCardProperties).invoke(new BoardEntity.ReplaceCardProperties(
        "card-1", Map.of(STATUS, PropertyValue.of("opt-doing")), T0 + 60_000));

    var card = kit.getState().cards().get("card-1");
    assertEquals("opt-doing", card.property(STATUS).asText());
    assertFalse(card.properties().containsKey(PRIORITY),
        "the property map is replaced wholesale, not merged into");
    assertEquals("Alpha", card.title(), "an unnamed field is untouched");
  }

  @Test
  public void patchMovesUpdateAtAndLeavesCreateAt() {
    var kit = seeded();
    kit.method(BoardEntity::replaceCardProperties).invoke(new BoardEntity.ReplaceCardProperties(
        "card-1", Map.of(STATUS, PropertyValue.of("opt-doing")), T0 + 60_000));
    var card = kit.getState().cards().get("card-1");
    assertEquals(T0, card.createAt());
    assertEquals(T0 + 60_000, card.updateAt());
  }

  @Test
  public void reorderingOneViewLeavesAnotherAlone() {
    var kit = seeded();
    var before = kit.getState().views().get("view-table").cardOrder();
    kit.method(BoardEntity::changeViewCardOrder).invoke(new BoardEntity.ChangeViewCardOrder(
        "view-kanban", List.of("card-5", "card-4", "card-3", "card-2", "card-1"), T0 + 60_000));

    assertEquals(List.of("card-5", "card-4", "card-3", "card-2", "card-1"),
        kit.getState().views().get("view-kanban").cardOrder());
    assertEquals(before, kit.getState().views().get("view-table").cardOrder(),
        "a card order belongs to the view it was written on");
    assertEquals(T0, kit.getState().cards().get("card-1").createAt());
    assertEquals(T0, kit.getState().cards().get("card-1").updateAt(),
        "reordering touches no card");
  }

  @Test
  public void patchingAnAbsentBlockIsRefusedAndChangesNothing() {
    var kit = seeded();
    var before = kit.getState();
    var result = kit.method(BoardEntity::replaceCardProperties)
        .invoke(new BoardEntity.ReplaceCardProperties("card-nowhere", Map.of(), T0));
    assertTrue(result.isError());
    assertTrue(result.getError().contains("card-nowhere"));
    assertEquals(before, kit.getState());
  }

  @Test
  public void aMoveIsOneEventCarryingBothWrites() {
    var kit = seeded();
    var result = kit.method(BoardEntity::moveToColumn)
        .invoke(new BoardEntity.MoveToColumn("view-kanban", "card-1", "opt-doing", T0 + 60_000));

    var event = result.getNextEventOfType(BoardEvent.CardMoved.class);
    assertTrue(event.propertyChanged());
    assertTrue(event.writesOrder());
    assertEquals("opt-doing", event.newOptionId());
    // The Doing column holds only card-2, so card-1 lands immediately after it.
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4", "card-5"), event.cardOrder());

    assertEquals("opt-doing", kit.getState().cards().get("card-1").property(STATUS).asText());
    assertEquals(event.cardOrder(), kit.getState().views().get("view-kanban").cardOrder());

    DerivedView derived = result.getReply();
    assertEquals(List.of("card-1"), derived.visible().stream()
        .filter(g -> g.optionId().equals("opt-doing")).findFirst().orElseThrow().cardIds()
        .stream().filter(id -> id.equals("card-1")).toList());
  }

  @Test
  public void movingACardInOneViewLeavesTheOtherViewsOrderAlone() {
    var kit = seeded();
    var before = kit.getState().views().get("view-table").cardOrder();
    kit.method(BoardEntity::moveToColumn)
        .invoke(new BoardEntity.MoveToColumn("view-kanban", "card-1", "opt-doing", T0 + 60_000));
    assertEquals(before, kit.getState().views().get("view-table").cardOrder());
  }

  @Test
  public void movingToTheCardsOwnColumnWritesNoProperty() {
    var kit = seeded();
    var result = kit.method(BoardEntity::moveToColumn)
        .invoke(new BoardEntity.MoveToColumn("view-kanban", "card-1", "opt-todo", T0 + 60_000));
    var event = result.getNextEventOfType(BoardEvent.CardMoved.class);
    assertFalse(event.propertyChanged());
    assertEquals(List.of("card-2", "card-3", "card-4", "card-1", "card-5"), event.cardOrder());
  }

  @Test
  public void movingOntoACardUsesTheDisplayedOrder() {
    var kit = seeded();
    // card-1 onto card-2, which is in another column: the dragged card lands at the target's
    // index rather than after it, so it keeps the front of the order.
    var acrossColumns = kit.method(BoardEntity::moveOntoCard)
        .invoke(new BoardEntity.MoveOntoCard("view-kanban", "card-1", "card-2", T0 + 60_000));
    assertEquals(List.of("card-1", "card-2", "card-3", "card-4", "card-5"),
        acrossColumns.getNextEventOfType(BoardEvent.CardMoved.class).cardOrder());

    // card-1 onto card-4, which shares its column -- from a fresh board, because the drop
    // above moved card-1 into Doing and the +1 only applies within one column.
    var withinAColumn = seeded().method(BoardEntity::moveOntoCard)
        .invoke(new BoardEntity.MoveOntoCard("view-kanban", "card-1", "card-4", T0 + 61_000));
    assertEquals(List.of("card-2", "card-3", "card-4", "card-1", "card-5"),
        withinAColumn.getNextEventOfType(BoardEvent.CardMoved.class).cardOrder());
  }

  @Test
  public void derivesTheSameAnswerTheDomainDoes() {
    var kit = seeded();
    kit.method(BoardEntity::changeViewFilter).invoke(new BoardEntity.ChangeViewFilter(
        "view-table",
        new FilterGroup("and", List.of(
            new io.akka.focalboard.domain.FilterClause(STATUS, "notIncludes", List.of("opt-done"))),
            List.of()),
        T0 + 60_000));
    kit.method(BoardEntity::changeViewSort).invoke(new BoardEntity.ChangeViewSort(
        "view-table", List.of(new SortOption(PRIORITY, false)), T0 + 60_000));

    DerivedView derived = kit.method(BoardEntity::derive)
        .invoke(new BoardEntity.Derive("view-table", "")).getReply();
    assertEquals(List.of("card-2", "card-4", "card-1", "card-5"), derived.orderedCardIds());
  }

  @Test
  public void deriveRefusesAViewTheBoardDoesNotHave() {
    var kit = seeded();
    var result = kit.method(BoardEntity::derive).invoke(new BoardEntity.Derive("view-nowhere", ""));
    assertTrue(result.isError());
  }

  @Test
  public void creatingABoardTwiceIsRefused() {
    var kit = seeded();
    var result = kit.method(BoardEntity::create)
        .invoke(new BoardEntity.CreateBoard("team-1", "Again", List.of(), T0));
    assertTrue(result.isError());
  }

  @Test
  public void aSequenceOfCommandsAccumulates() {
    var kit = seeded();
    kit.method(BoardEntity::moveToColumn)
        .invoke(new BoardEntity.MoveToColumn("view-kanban", "card-1", "opt-doing", T0 + 60_000));
    kit.method(BoardEntity::changeCardTitle)
        .invoke(new BoardEntity.ChangeCardTitle("card-2", "Bravo II", T0 + 61_000));
    kit.method(BoardEntity::deleteCard).invoke("card-5");

    assertFalse(kit.getState().cards().containsKey("card-5"));
    assertEquals("Bravo II", kit.getState().cards().get("card-2").title());
    assertEquals("opt-doing", kit.getState().cards().get("card-1").property(STATUS).asText());
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4", "card-5"),
        kit.getState().views().get("view-kanban").cardOrder(),
        "a deleted card keeps its place in the order, the way a block delete leaves the view's own field alone");
  }
}
