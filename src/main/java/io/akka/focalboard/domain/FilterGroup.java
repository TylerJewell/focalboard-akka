package io.akka.focalboard.domain;

import java.util.List;

/**
 * A group of tests, joined by {@code and} or {@code or}, which may hold groups of its own.
 *
 * <p>Nesting is expressed as two lists rather than one list of a sum type, for the reason
 * {@link FilterClause} gives. Evaluation order between the two is immaterial: {@code and}
 * requires all of both and {@code or} requires any of either.
 */
public record FilterGroup(String operation, List<FilterClause> clauses, List<FilterGroup> groups) {

  public static final String AND = "and";
  public static final String OR = "or";

  public static FilterGroup empty() {
    return new FilterGroup(AND, List.of(), List.of());
  }

  public boolean isEmpty() {
    return clauses.isEmpty() && groups.isEmpty();
  }
}
