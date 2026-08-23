package io.akka.focalboard.domain;

/** One step of a view's sort. {@code propertyId} may be {@code __title}, the title pseudo-property. */
public record SortOption(String propertyId, boolean reversed) {

  public static final String TITLE = "__title";
}
