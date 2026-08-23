package io.akka.focalboard.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.util.List;
import java.util.Optional;

/**
 * The boards a team has, which is what the interface asks for before it can open one.
 *
 * <p>{@code title} and {@code firstViewId} are typed {@code Optional} because a board exists
 * for a moment before it has either, and a row whose plain {@code String} field is null stops
 * the view's update stream — which does not fail, it empties every query against the view
 * and says nothing.
 *
 * <p>The wrapper's field is {@code boards} rather than {@code rows} because {@code rows} is a
 * reserved word in the query language, and the refusal arrives at service startup.
 */
@Component(id = "boards-by-team")
public class BoardsByTeamView extends View {

  public record BoardRow(String boardId, String teamId, Optional<String> title,
                         Optional<String> firstViewId, int cardCount, int viewCount,
                         long createAt, long updateAt) {}

  public record BoardRows(List<BoardRow> boards) {}

  @Consume.FromEventSourcedEntity(BoardEntity.class)
  public static class Boards extends TableUpdater<BoardRow> {

    public Effect<BoardRow> onEvent(BoardEvent event) {
      var boardId = updateContext().eventSubject().orElse("");
      var row = rowState();
      return switch (event) {
        case BoardEvent.BoardCreated e -> effects().updateRow(new BoardRow(
            e.boardId(), e.teamId(), Optional.ofNullable(emptyToNull(e.title())),
            Optional.empty(), 0, 0, e.at(), e.at()));

        case BoardEvent.CardsAdded e -> row == null ? effects().ignore()
            : effects().updateRow(new BoardRow(row.boardId(), row.teamId(), row.title(),
                row.firstViewId(), row.cardCount() + e.cards().size(), row.viewCount(),
                row.createAt(), row.updateAt()));

        case BoardEvent.ViewsAdded e -> row == null ? effects().ignore()
            : effects().updateRow(new BoardRow(row.boardId(), row.teamId(), row.title(),
                row.firstViewId().or(() -> e.views().isEmpty() ? Optional.empty()
                    : Optional.of(e.views().get(0).id())),
                row.cardCount(), row.viewCount() + e.views().size(),
                row.createAt(), row.updateAt()));

        case BoardEvent.CardDeleted e -> row == null ? effects().ignore()
            : effects().updateRow(new BoardRow(row.boardId(), row.teamId(), row.title(),
                row.firstViewId(), Math.max(0, row.cardCount() - 1), row.viewCount(),
                row.createAt(), e.at()));

        default -> row == null ? effects().ignore()
            : effects().updateRow(new BoardRow(row.boardId(), row.teamId(), row.title(),
                row.firstViewId(), row.cardCount(), row.viewCount(),
                row.createAt(), touchedAt(event, row.updateAt(), boardId)));
      };
    }

    private static long touchedAt(BoardEvent event, long fallback, String boardId) {
      return switch (event) {
        case BoardEvent.CardPropertiesReplaced e -> e.at();
        case BoardEvent.CardTitleChanged e -> e.at();
        case BoardEvent.ViewCardOrderChanged e -> e.at();
        case BoardEvent.ViewFilterChanged e -> e.at();
        case BoardEvent.ViewSortChanged e -> e.at();
        case BoardEvent.ViewGroupByChanged e -> e.at();
        case BoardEvent.ViewOptionVisibilityChanged e -> e.at();
        case BoardEvent.CardMoved e -> e.at();
        default -> fallback;
      };
    }

    private static String emptyToNull(String value) {
      return value == null || value.isEmpty() ? null : value;
    }
  }

  @Query("SELECT * AS boards FROM boards_by_team WHERE teamId = :teamId")
  public QueryEffect<BoardRows> byTeam(String teamId) {
    return queryResult();
  }

  @Query("SELECT * AS boards FROM boards_by_team")
  public QueryEffect<BoardRows> all() {
    return queryResult();
  }
}
