package io.akka.focalboard.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.akka.focalboard.domain.Card;
import io.akka.focalboard.domain.CardMovement;
import io.akka.focalboard.domain.DerivedView;
import io.akka.focalboard.domain.FilterGroup;
import io.akka.focalboard.domain.PropertyTemplate;
import io.akka.focalboard.domain.PropertyValue;
import io.akka.focalboard.domain.SortOption;
import io.akka.focalboard.domain.ViewDerivation;
import io.akka.focalboard.domain.ViewSpec;
import java.util.List;
import java.util.Map;

/**
 * One board, holding its cards and its views and answering what each view shows.
 *
 * <p>The derivation is a read-only command rather than a stored projection: the answer
 * depends on a search string the caller supplies, so there is nothing to project, and the
 * whole of what it reads is already in this entity's own state.
 */
@Component(id = "board")
public class BoardEntity extends EventSourcedEntity<BoardState, BoardEvent> {

  public record CreateBoard(String teamId, String title, List<PropertyTemplate> cardProperties,
                            long at) {}

  public record AddCards(List<Card> cards) {}

  public record AddViews(List<ViewSpec> views) {}

  public record ReplaceCardProperties(String cardId, Map<String, PropertyValue> properties,
                                      long at) {}

  public record ChangeCardTitle(String cardId, String title, long at) {}

  public record ChangeViewCardOrder(String viewId, List<String> cardOrder, long at) {}

  public record ChangeViewFilter(String viewId, FilterGroup filter, long at) {}

  public record ChangeViewSort(String viewId, List<SortOption> sortOptions, long at) {}

  public record ChangeViewGroupBy(String viewId, String groupById, long at) {}

  public record ChangeViewOptionVisibility(String viewId, List<String> visibleOptionIds,
                                           List<String> hiddenOptionIds, long at) {}

  public record ChangeViewVisibleProperties(String viewId, List<String> visiblePropertyIds,
                                            long at) {}

  /** Drop on a column. {@code targetOptionId} is empty for the empty column. */
  public record MoveToColumn(String viewId, String cardId, String targetOptionId, long at) {}

  /** Drop on another card. */
  public record MoveOntoCard(String viewId, String cardId, String targetCardId, long at) {}

  public record Derive(String viewId, String searchText) {}

  @Override
  public BoardState emptyState() {
    return BoardState.empty();
  }

  public Effect<String> create(CreateBoard command) {
    if (currentState().exists()) {
      return effects().error("board " + commandContext().entityId() + " already exists");
    }
    return effects()
        .persist(new BoardEvent.BoardCreated(commandContext().entityId(), command.teamId(),
            command.title(), command.cardProperties(), command.at()))
        .thenReply(state -> state.boardId());
  }

  public ReadOnlyEffect<BoardState> read() {
    return effects().reply(currentState());
  }

  public Effect<String> addCards(AddCards command) {
    if (!currentState().exists()) {
      return effects().error("no such board");
    }
    return effects().persist(new BoardEvent.CardsAdded(command.cards())).thenReply(s -> "ok");
  }

  public Effect<String> addViews(AddViews command) {
    if (!currentState().exists()) {
      return effects().error("no such board");
    }
    return effects().persist(new BoardEvent.ViewsAdded(command.views())).thenReply(s -> "ok");
  }

  /** R16: the property map is replaced, not merged into. */
  public Effect<String> replaceCardProperties(ReplaceCardProperties command) {
    if (!currentState().cards().containsKey(command.cardId())) {
      return effects().error("{block ID=" + command.cardId() + "} not found");
    }
    return effects().persist(new BoardEvent.CardPropertiesReplaced(
        command.cardId(), command.properties(), command.at())).thenReply(s -> "ok");
  }

  public Effect<String> changeCardTitle(ChangeCardTitle command) {
    if (!currentState().cards().containsKey(command.cardId())) {
      return effects().error("{block ID=" + command.cardId() + "} not found");
    }
    return effects().persist(new BoardEvent.CardTitleChanged(
        command.cardId(), command.title(), command.at())).thenReply(s -> "ok");
  }

  public Effect<String> deleteCard(String cardId) {
    if (!currentState().cards().containsKey(cardId)) {
      return effects().error("{block ID=" + cardId + "} not found");
    }
    return effects().persist(new BoardEvent.CardDeleted(cardId, System.currentTimeMillis()))
        .thenReply(s -> "ok");
  }

  /** R18: an order belongs to one view, so this touches nothing else. */
  public Effect<String> changeViewCardOrder(ChangeViewCardOrder command) {
    if (!currentState().views().containsKey(command.viewId())) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    return effects().persist(new BoardEvent.ViewCardOrderChanged(
        command.viewId(), command.cardOrder(), command.at())).thenReply(s -> "ok");
  }

  public Effect<String> changeViewFilter(ChangeViewFilter command) {
    if (!currentState().views().containsKey(command.viewId())) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    return effects().persist(new BoardEvent.ViewFilterChanged(
        command.viewId(), command.filter(), command.at())).thenReply(s -> "ok");
  }

  public Effect<String> changeViewSort(ChangeViewSort command) {
    if (!currentState().views().containsKey(command.viewId())) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    return effects().persist(new BoardEvent.ViewSortChanged(
        command.viewId(), command.sortOptions(), command.at())).thenReply(s -> "ok");
  }

  public Effect<String> changeViewGroupBy(ChangeViewGroupBy command) {
    if (!currentState().views().containsKey(command.viewId())) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    return effects().persist(new BoardEvent.ViewGroupByChanged(
        command.viewId(), command.groupById(), command.at())).thenReply(s -> "ok");
  }

  public Effect<String> changeViewOptionVisibility(ChangeViewOptionVisibility command) {
    if (!currentState().views().containsKey(command.viewId())) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    return effects().persist(new BoardEvent.ViewOptionVisibilityChanged(command.viewId(),
        command.visibleOptionIds(), command.hiddenOptionIds(), command.at()))
        .thenReply(s -> "ok");
  }

  public Effect<String> changeViewVisibleProperties(ChangeViewVisibleProperties command) {
    if (!currentState().views().containsKey(command.viewId())) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    return effects().persist(new BoardEvent.ViewVisiblePropertiesChanged(
        command.viewId(), command.visiblePropertyIds(), command.at())).thenReply(s -> "ok");
  }

  /**
   * R12–R14, and D3: the property write and the order write are one event, so they land
   * together or not at all.
   */
  public Effect<DerivedView> moveToColumn(MoveToColumn command) {
    var view = currentState().views().get(command.viewId());
    if (view == null) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    var dragged = currentState().cards().get(command.cardId());
    if (dragged == null) {
      return effects().error("{block ID=" + command.cardId() + "} not found");
    }
    var groupBy = currentState().property(view.groupById());
    if (groupBy == null) {
      return effects().error("view " + view.id() + " has no group-by property");
    }

    // The column's current last card comes from the derivation, because that is what the
    // interface was showing when the card was dropped.
    var derived = derive(view, "");
    var targetColumn = derived.visible().stream()
        .filter(g -> g.optionId().equals(command.targetOptionId()))
        .findFirst()
        .or(() -> derived.hidden().stream()
            .filter(g -> g.optionId().equals(command.targetOptionId())).findFirst());
    var columnCardIds = targetColumn.map(g -> g.cardIds()).orElse(List.of());

    var move = CardMovement.dropOnColumn(view, groupBy, dragged, columnCardIds,
        command.targetOptionId().isEmpty() ? null : command.targetOptionId());

    return effects().persist(new BoardEvent.CardMoved(move.cardId(), view.id(), move.propertyId(),
            move.newOptionId(), move.propertyChanged(), move.writesOrder(), move.cardOrder(),
            command.at()))
        .thenReply(state -> derive(state, view.id(), ""));
  }

  /** R15: this route rebuilds the order from what was on screen, not from the stored one. */
  public Effect<DerivedView> moveOntoCard(MoveOntoCard command) {
    var view = currentState().views().get(command.viewId());
    if (view == null) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    var dragged = currentState().cards().get(command.cardId());
    var target = currentState().cards().get(command.targetCardId());
    if (dragged == null || target == null) {
      return effects().error("{block ID=" + (dragged == null ? command.cardId()
          : command.targetCardId()) + "} not found");
    }
    var groupBy = currentState().property(view.groupById());
    if (groupBy == null) {
      return effects().error("view " + view.id() + " has no group-by property");
    }

    var displayed = derive(view, "").orderedCardIds();
    var move = CardMovement.dropOnCard(view, groupBy, dragged, target, displayed);

    return effects().persist(new BoardEvent.CardMoved(move.cardId(), view.id(), move.propertyId(),
            move.newOptionId(), move.propertyChanged(), move.writesOrder(), move.cardOrder(),
            command.at()))
        .thenReply(state -> derive(state, view.id(), ""));
  }

  public ReadOnlyEffect<DerivedView> derive(Derive command) {
    var view = currentState().views().get(command.viewId());
    if (view == null) {
      return effects().error("{block ID=" + command.viewId() + "} not found");
    }
    return effects().reply(derive(view, command.searchText() == null ? "" : command.searchText()));
  }

  private DerivedView derive(ViewSpec view, String searchText) {
    return ViewDerivation.derive(currentState().cardProperties(),
        currentState().orderedCards(), view, searchText);
  }

  private static DerivedView derive(BoardState state, String viewId, String searchText) {
    return ViewDerivation.derive(state.cardProperties(), state.orderedCards(),
        state.views().get(viewId), searchText);
  }

  @Override
  public BoardState applyEvent(BoardEvent event) {
    var state = currentState();
    return switch (event) {
      case BoardEvent.BoardCreated e -> new BoardState(e.boardId(), e.teamId(), e.title(),
          List.copyOf(e.cardProperties()), Map.of(), Map.of(), e.at(), e.at());

      case BoardEvent.CardsAdded e -> {
        var cards = state.mutableCards();
        for (var card : e.cards()) {
          cards.put(card.id(), card);
        }
        yield state.withCards(cards, state.updateAt());
      }

      case BoardEvent.ViewsAdded e -> {
        var views = state.mutableViews();
        for (var view : e.views()) {
          views.put(view.id(), view);
        }
        yield state.withViews(views, state.updateAt());
      }

      case BoardEvent.CardPropertiesReplaced e -> {
        var cards = state.mutableCards();
        cards.computeIfPresent(e.cardId(),
            (id, card) -> card.withProperties(e.properties(), e.at()));
        yield state.withCards(cards, e.at());
      }

      case BoardEvent.CardTitleChanged e -> {
        var cards = state.mutableCards();
        cards.computeIfPresent(e.cardId(), (id, card) -> card.withTitle(e.title(), e.at()));
        yield state.withCards(cards, e.at());
      }

      case BoardEvent.CardDeleted e -> {
        var cards = state.mutableCards();
        cards.remove(e.cardId());
        yield state.withCards(cards, e.at());
      }

      case BoardEvent.ViewCardOrderChanged e -> {
        var views = state.mutableViews();
        views.computeIfPresent(e.viewId(), (id, view) -> view.withCardOrder(e.cardOrder()));
        yield state.withViews(views, e.at());
      }

      case BoardEvent.ViewFilterChanged e -> {
        var views = state.mutableViews();
        views.computeIfPresent(e.viewId(), (id, view) -> view.withFilter(e.filter()));
        yield state.withViews(views, e.at());
      }

      case BoardEvent.ViewSortChanged e -> {
        var views = state.mutableViews();
        views.computeIfPresent(e.viewId(), (id, view) -> view.withSortOptions(e.sortOptions()));
        yield state.withViews(views, e.at());
      }

      case BoardEvent.ViewGroupByChanged e -> {
        var views = state.mutableViews();
        views.computeIfPresent(e.viewId(), (id, view) -> view.withGroupById(e.groupById()));
        yield state.withViews(views, e.at());
      }

      case BoardEvent.ViewVisiblePropertiesChanged e -> {
        var views = state.mutableViews();
        views.computeIfPresent(e.viewId(),
            (id, view) -> view.withVisibleProperties(e.visiblePropertyIds()));
        yield state.withViews(views, e.at());
      }

      case BoardEvent.ViewOptionVisibilityChanged e -> {
        var views = state.mutableViews();
        views.computeIfPresent(e.viewId(),
            (id, view) -> view.withOptionVisibility(e.visibleOptionIds(), e.hiddenOptionIds()));
        yield state.withViews(views, e.at());
      }

      case BoardEvent.CardMoved e -> {
        var next = state;
        if (e.propertyChanged()) {
          var cards = next.mutableCards();
          cards.computeIfPresent(e.cardId(), (id, card) -> {
            var properties = new java.util.LinkedHashMap<>(card.properties());
            if (e.newOptionId() == null) {
              properties.remove(e.propertyId());
            } else {
              properties.put(e.propertyId(), PropertyValue.of(e.newOptionId()));
            }
            return card.withProperties(properties, e.at());
          });
          next = next.withCards(cards, e.at());
        }
        if (e.writesOrder()) {
          var views = next.mutableViews();
          views.computeIfPresent(e.viewId(), (id, view) -> view.withCardOrder(e.cardOrder()));
          next = next.withViews(views, e.at());
        }
        yield next;
      }
    };
  }
}
