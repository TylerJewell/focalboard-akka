package io.akka.focalboard.domain;

import java.util.List;

/**
 * A view over a board's cards.
 *
 * <p>{@code cardOrder} is the field that makes a card's position belong to the view rather
 * than to the card: two views over the same cards hold two orders, and moving a card in one
 * leaves the other alone.
 */
public record ViewSpec(String id, String title, String viewType, String groupById,
                       List<SortOption> sortOptions, FilterGroup filter,
                       List<String> cardOrder,
                       List<String> visibleOptionIds, List<String> hiddenOptionIds,
                       List<String> visiblePropertyIds) {

  /** A view with nothing shown on its cards beyond their titles. */
  public static ViewSpec of(String id, String title, String viewType, String groupById,
                            List<SortOption> sortOptions, FilterGroup filter,
                            List<String> cardOrder, List<String> visibleOptionIds,
                            List<String> hiddenOptionIds) {
    return new ViewSpec(id, title, viewType, groupById, sortOptions, filter, cardOrder,
        visibleOptionIds, hiddenOptionIds, List.of());
  }

  public ViewSpec withCardOrder(List<String> order) {
    return new ViewSpec(id, title, viewType, groupById, sortOptions, filter,
        List.copyOf(order), visibleOptionIds, hiddenOptionIds, visiblePropertyIds);
  }

  public ViewSpec withFilter(FilterGroup replacement) {
    return new ViewSpec(id, title, viewType, groupById, sortOptions, replacement,
        cardOrder, visibleOptionIds, hiddenOptionIds, visiblePropertyIds);
  }

  public ViewSpec withSortOptions(List<SortOption> replacement) {
    return new ViewSpec(id, title, viewType, groupById, replacement, filter,
        cardOrder, visibleOptionIds, hiddenOptionIds, visiblePropertyIds);
  }

  public ViewSpec withGroupById(String propertyId) {
    return new ViewSpec(id, title, viewType, propertyId, sortOptions, filter,
        cardOrder, visibleOptionIds, hiddenOptionIds, visiblePropertyIds);
  }

  public ViewSpec withVisibleProperties(List<String> propertyIds) {
    return new ViewSpec(id, title, viewType, groupById, sortOptions, filter,
        cardOrder, visibleOptionIds, hiddenOptionIds, List.copyOf(propertyIds));
  }

  public ViewSpec withOptionVisibility(List<String> visible, List<String> hidden) {
    return new ViewSpec(id, title, viewType, groupById, sortOptions, filter,
        cardOrder, List.copyOf(visible), List.copyOf(hidden), visiblePropertyIds);
  }
}
