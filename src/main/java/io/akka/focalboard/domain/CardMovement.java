package io.akka.focalboard.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * What a card being dragged writes back (SPEC R12–R15).
 *
 * <p>Two routes, and they do not share a starting point. Dropping on a **column** rewrites
 * the view's stored {@code cardOrder}; dropping on a **card** rebuilds the order from what
 * is on screen and discards the stored one. Both are the original's, and the difference
 * between them is visible whenever the two disagree.
 */
public final class CardMovement {

  /**
   * @param propertyChanged whether the group-by property is written at all
   * @param writesOrder whether the card order is written at all
   */
  public record MoveResult(String cardId, String propertyId, String newOptionId,
                           boolean propertyChanged, boolean writesOrder, List<String> cardOrder) {}

  private CardMovement() {}

  /**
   * R12, R13, R14. The dragged card is removed from the order and re-inserted immediately
   * after the target column's current last card.
   *
   * <p>Where that last card is not itself in the order, the position it would have had is
   * −1 and the insert lands at the front. Where the target column has no cards at all, the
   * order is written back exactly as it was.
   */
  public static MoveResult dropOnColumn(ViewSpec view, PropertyTemplate groupBy, Card dragged,
                                        List<String> targetColumnCardIds, String targetOptionId) {
    var current = currentOption(dragged, groupBy);
    var changed = !java.util.Objects.equals(current, targetOptionId);

    if (targetColumnCardIds.isEmpty()) {
      return new MoveResult(dragged.id(), groupBy.id(), targetOptionId, changed, true,
          view.cardOrder());
    }

    var anchor = targetColumnCardIds.get(targetColumnCardIds.size() - 1);
    var order = new ArrayList<>(view.cardOrder());
    order.remove(dragged.id());
    var at = order.indexOf(anchor);
    order.add(at + 1, dragged.id());
    return new MoveResult(dragged.id(), groupBy.id(), targetOptionId, changed, true,
        List.copyOf(order));
  }

  /**
   * R15. The order is rebuilt from the displayed card list rather than from the view's
   * stored one, with the dragged card removed and placed at the target's position — one
   * later when the two share a column and the dragged card was displayed above the target.
   */
  public static MoveResult dropOnCard(ViewSpec view, PropertyTemplate groupBy, Card dragged,
                                      Card target, List<String> displayedCardIds) {
    if (dragged.id().equals(target.id())) {
      return new MoveResult(dragged.id(), groupBy.id(), currentOption(dragged, groupBy),
          false, false, view.cardOrder());
    }

    var targetOptionId = currentOption(target, groupBy);
    var draggedOptionId = currentOption(dragged, groupBy);
    var changed = !java.util.Objects.equals(draggedOptionId, targetOptionId);

    var order = new ArrayList<>(displayedCardIds);
    var draggingDown = order.indexOf(dragged.id()) <= order.indexOf(target.id());
    order.remove(dragged.id());
    var at = order.indexOf(target.id());
    if (java.util.Objects.equals(draggedOptionId, targetOptionId) && draggingDown) {
      at += 1;
    }
    order.add(at, dragged.id());
    return new MoveResult(dragged.id(), groupBy.id(), targetOptionId, changed, true,
        List.copyOf(order));
  }

  private static String currentOption(Card card, PropertyTemplate groupBy) {
    var value = card.property(groupBy.id());
    if (value == null || value.length() == 0) {
      return null;
    }
    return value.multi() ? value.values().get(0) : value.asText();
  }
}
