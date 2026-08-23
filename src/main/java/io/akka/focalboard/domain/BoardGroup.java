package io.akka.focalboard.domain;

import java.util.List;

/**
 * One column of a derived view: the option it stands for, and the cards in it, in the order
 * the sort put them.
 *
 * <p>The empty column carries an option id of {@code ""} and the label {@code "No <property
 * name>"}, which is how the original names it.
 */
public record BoardGroup(String optionId, String label, String color, List<String> cardIds) {}
