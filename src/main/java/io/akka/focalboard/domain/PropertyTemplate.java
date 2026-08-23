package io.akka.focalboard.domain;

import java.util.List;
import java.util.Optional;

/**
 * One property a board defines for its cards.
 *
 * <p>{@code options} is ordered, and that order decides where a column lands when the view
 * pins neither its visibility nor its position (SPEC R8).
 */
public record PropertyTemplate(String id, String name, String type, List<PropertyOption> options) {

  public Optional<PropertyOption> option(String optionId) {
    return options.stream().filter(o -> o.id().equals(optionId)).findFirst();
  }

  public boolean defines(String optionId) {
    return option(optionId).isPresent();
  }
}
