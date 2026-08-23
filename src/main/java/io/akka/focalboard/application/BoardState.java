package io.akka.focalboard.application;

import io.akka.focalboard.domain.Card;
import io.akka.focalboard.domain.PropertyTemplate;
import io.akka.focalboard.domain.ViewSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One whole board: its property definitions, its cards and its views.
 *
 * <p>All three live in one state because deciding what a view shows needs all three at once,
 * and because a move writes a card and a view together. The target holds 8,000 cards in one
 * entity comfortably; what it refuses is a single command above a megabyte, which a per-card
 * command never approaches.
 */
public record BoardState(String boardId, String teamId, String title,
                         List<PropertyTemplate> cardProperties,
                         Map<String, Card> cards, Map<String, ViewSpec> views,
                         long createAt, long updateAt) {

  public static BoardState empty() {
    return new BoardState("", "", "", List.of(), Map.of(), Map.of(), 0, 0);
  }

  public boolean exists() {
    return !boardId.isEmpty();
  }

  public BoardState withCards(Map<String, Card> replacement, long at) {
    return new BoardState(boardId, teamId, title, cardProperties,
        Map.copyOf(replacement), views, createAt, at);
  }

  public BoardState withViews(Map<String, ViewSpec> replacement, long at) {
    return new BoardState(boardId, teamId, title, cardProperties,
        cards, Map.copyOf(replacement), createAt, at);
  }

  public Map<String, Card> mutableCards() {
    return new LinkedHashMap<>(cards);
  }

  public Map<String, ViewSpec> mutableViews() {
    return new LinkedHashMap<>(views);
  }

  public List<Card> orderedCards() {
    return List.copyOf(cards.values());
  }

  public PropertyTemplate property(String propertyId) {
    if (propertyId == null) {
      return null;
    }
    return cardProperties.stream().filter(p -> p.id().equals(propertyId)).findFirst().orElse(null);
  }
}
