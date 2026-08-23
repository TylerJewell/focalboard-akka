package io.akka.focalboard.domain;

import java.util.List;

/**
 * What one view answers about one board's cards: which cards it shows, in what order, and
 * which columns they fall in.
 *
 * <p>{@code visible} and {@code hidden} are empty when the view names no group-by property,
 * in which case {@code orderedCardIds} is the whole answer.
 */
public record DerivedView(String viewId, List<String> orderedCardIds,
                          List<BoardGroup> visible, List<BoardGroup> hidden) {}
