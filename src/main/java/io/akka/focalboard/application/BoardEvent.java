package io.akka.focalboard.application;

import akka.javasdk.annotations.TypeName;
import io.akka.focalboard.domain.Card;
import io.akka.focalboard.domain.FilterGroup;
import io.akka.focalboard.domain.PropertyTemplate;
import io.akka.focalboard.domain.PropertyValue;
import io.akka.focalboard.domain.SortOption;
import io.akka.focalboard.domain.ViewSpec;
import java.util.List;
import java.util.Map;

/**
 * What has happened to a board.
 *
 * <p>One event per field a patch may name, rather than one generic patch event, because SPEC
 * R16 is that a named field is replaced wholesale and an unnamed one is untouched — which is
 * exactly what a per-field event says, and what a bag of updated fields would leave for
 * every reader to re-derive.
 */
public sealed interface BoardEvent {

  @TypeName("board-created")
  record BoardCreated(String boardId, String teamId, String title,
                      List<PropertyTemplate> cardProperties, long at) implements BoardEvent {}

  @TypeName("cards-added")
  record CardsAdded(List<Card> cards) implements BoardEvent {}

  @TypeName("views-added")
  record ViewsAdded(List<ViewSpec> views) implements BoardEvent {}

  @TypeName("card-properties-replaced")
  record CardPropertiesReplaced(String cardId, Map<String, PropertyValue> properties, long at)
      implements BoardEvent {}

  @TypeName("card-title-changed")
  record CardTitleChanged(String cardId, String title, long at) implements BoardEvent {}

  @TypeName("card-deleted")
  record CardDeleted(String cardId, long at) implements BoardEvent {}

  @TypeName("view-card-order-changed")
  record ViewCardOrderChanged(String viewId, List<String> cardOrder, long at)
      implements BoardEvent {}

  @TypeName("view-filter-changed")
  record ViewFilterChanged(String viewId, FilterGroup filter, long at) implements BoardEvent {}

  @TypeName("view-sort-changed")
  record ViewSortChanged(String viewId, List<SortOption> sortOptions, long at)
      implements BoardEvent {}

  @TypeName("view-group-by-changed")
  record ViewGroupByChanged(String viewId, String groupById, long at) implements BoardEvent {}

  @TypeName("view-visible-properties-changed")
  record ViewVisiblePropertiesChanged(String viewId, List<String> visiblePropertyIds, long at)
      implements BoardEvent {}

  @TypeName("view-option-visibility-changed")
  record ViewOptionVisibilityChanged(String viewId, List<String> visibleOptionIds,
                                     List<String> hiddenOptionIds, long at) implements BoardEvent {}

  /**
   * A move, as one event.
   *
   * <p>The original issues two independent HTTP calls and nothing ties them (SPEC D3). Here
   * the property write and the order write are one fact, so a reader never sees a card in
   * its new column and the old order at the same time.
   */
  @TypeName("card-moved")
  record CardMoved(String cardId, String viewId, String propertyId, String newOptionId,
                   boolean propertyChanged, boolean writesOrder, List<String> cardOrder, long at)
      implements BoardEvent {}
}
