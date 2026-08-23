package io.akka.focalboard.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import io.akka.focalboard.application.BoardEntity;
import io.akka.focalboard.application.BoardState;
import io.akka.focalboard.application.BoardsByTeamView;
import io.akka.focalboard.domain.Card;
import io.akka.focalboard.domain.DerivedView;
import io.akka.focalboard.domain.PropertyTemplate;
import io.akka.focalboard.domain.ViewSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The part of focalboard's own API that carries this slice's state, plus the two routes that
 * are this port's own: the derivation, and the stream.
 *
 * <p>The board page calls thirteen endpoints. Two of them are here because they carry cards,
 * views and property definitions; the other eleven are the shell, and live in {@link
 * ShellEndpoint} as fixed shapes captured from the original.
 */
@HttpEndpoint("/api/v2")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class BoardApiEndpoint extends AbstractHttpEndpoint {

  /**
   * How many blocks go in one command. A card of this port's shape is a few hundred bytes,
   * so a thousand of them sits an order of magnitude below the runtime's 1 MB ceiling with
   * room for a card far larger than any the interface makes.
   */
  private static final int CARDS_PER_COMMAND = 1000;

  private final ComponentClient componentClient;

  public BoardApiEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // ---------------------------------------------------------------- the board

  @Get("/teams/{teamId}/boards")
  public List<Map<String, Object>> boardsOfTeam(String teamId) {
    var rows = componentClient.forView().method(BoardsByTeamView::byTeam).invoke(teamId).boards();
    var out = new ArrayList<Map<String, Object>>();
    for (var row : rows) {
      out.add(Blocks.boardJson(read(row.boardId())));
    }
    return List.copyOf(out);
  }

  @Get("/boards/{boardId}")
  public Map<String, Object> board(String boardId) {
    return Blocks.boardJson(requireBoard(boardId));
  }

  @Post("/boards")
  public Map<String, Object> createBoard(com.fasterxml.jackson.databind.JsonNode raw) {
    var body = Blocks.toMap(raw);
    var boardId = Blocks.asString(body.get("id"),
        "b" + UUID.randomUUID().toString().replace("-", ""));
    var properties = new ArrayList<PropertyTemplate>();
    for (var property : Blocks.asList(body.get("cardProperties"))) {
      properties.add(Blocks.propertyTemplate(property));
    }
    componentClient.forEventSourcedEntity(boardId).method(BoardEntity::create)
        .invoke(new BoardEntity.CreateBoard(Blocks.asString(body.get("teamId"), "0"),
            Blocks.asString(body.get("title"), ""), List.copyOf(properties),
            System.currentTimeMillis()));
    return Blocks.boardJson(read(boardId));
  }

  // --------------------------------------------------------------- the blocks

  @Get("/boards/{boardId}/blocks")
  public List<Map<String, Object>> blocks(String boardId) {
    return Blocks.blocksOf(requireBoard(boardId));
  }

  /**
   * Cards and views arrive together, keeping the ids they were given.
   *
   * <p>The original replaces every id here and does not rewrite {@code cardOrder} to match, so
   * a view posted alongside its cards comes back naming ids that no longer exist. SPEC D5: id
   * generation is not part of this slice, and copying a rewriting rule inconsistent with
   * itself would import a defect the port has no reason to carry.
   */
  @Post("/boards/{boardId}/blocks")
  public List<Map<String, Object>> insertBlocks(String boardId,
                                               com.fasterxml.jackson.databind.JsonNode raw) {
    var body = Blocks.toMaps(raw);
    requireBoard(boardId);
    long now = System.currentTimeMillis();
    var cards = new ArrayList<Card>();
    var views = new ArrayList<ViewSpec>();
    for (var block : body) {
      if ("view".equals(Blocks.asString(block.get("type"), ""))) {
        views.add(Blocks.view(block));
      } else {
        cards.add(Blocks.card(block, now));
      }
    }
    // A command's payload and metadata may not exceed 1,048,477 bytes, and the runtime
    // refuses the whole command rather than storing part of it. Measured on this target at
    // 20,000 cards in one call: `focalboard-port/probes/target-probe` T1. Chunking keeps a
    // large import from failing entirely at a size nothing in the code names.
    for (int from = 0; from < cards.size(); from += CARDS_PER_COMMAND) {
      var chunk = cards.subList(from, Math.min(cards.size(), from + CARDS_PER_COMMAND));
      componentClient.forEventSourcedEntity(boardId).method(BoardEntity::addCards)
          .invoke(new BoardEntity.AddCards(List.copyOf(chunk)));
    }
    for (int from = 0; from < views.size(); from += CARDS_PER_COMMAND) {
      var chunk = views.subList(from, Math.min(views.size(), from + CARDS_PER_COMMAND));
      componentClient.forEventSourcedEntity(boardId).method(BoardEntity::addViews)
          .invoke(new BoardEntity.AddViews(List.copyOf(chunk)));
    }
    return Blocks.blocksOf(read(boardId));
  }

  /**
   * R16: each named field is replaced wholesale and every unnamed one is left alone. A field
   * in {@code deletedFields} is replaced with nothing, which for {@code properties} is an
   * empty map.
   */
  @Patch("/boards/{boardId}/blocks/{blockId}")
  public Map<String, Object> patchBlock(String boardId, String blockId,
                                        com.fasterxml.jackson.databind.JsonNode raw) {
    var body = Blocks.toMap(raw);
    var state = requireBoard(boardId);
    long now = System.currentTimeMillis();
    var updated = Blocks.asMap(body.get("updatedFields"));
    var deleted = Blocks.asStrings(body.get("deletedFields"));

    if (state.cards().containsKey(blockId)) {
      if (body.get("title") instanceof String title) {
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::changeCardTitle)
            .invoke(new BoardEntity.ChangeCardTitle(blockId, title, now));
      }
      if (updated.containsKey("properties")) {
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::replaceCardProperties)
            .invoke(new BoardEntity.ReplaceCardProperties(
                blockId, Blocks.properties(updated.get("properties")), now));
      } else if (deleted.contains("properties")) {
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::replaceCardProperties)
            .invoke(new BoardEntity.ReplaceCardProperties(blockId, Map.of(), now));
      }
      return Blocks.cardBlock(boardId, read(boardId).cards().get(blockId));
    }

    if (state.views().containsKey(blockId)) {
      if (updated.containsKey("cardOrder")) {
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::changeViewCardOrder)
            .invoke(new BoardEntity.ChangeViewCardOrder(
                blockId, Blocks.asStrings(updated.get("cardOrder")), now));
      }
      if (updated.containsKey("filter")) {
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::changeViewFilter)
            .invoke(new BoardEntity.ChangeViewFilter(
                blockId, Blocks.filterGroup(updated.get("filter")), now));
      }
      if (updated.containsKey("sortOptions")) {
        var sorts = new ArrayList<io.akka.focalboard.domain.SortOption>();
        for (var sort : Blocks.asList(updated.get("sortOptions"))) {
          var s = Blocks.asMap(sort);
          sorts.add(new io.akka.focalboard.domain.SortOption(
              Blocks.asString(s.get("propertyId"), ""), Boolean.TRUE.equals(s.get("reversed"))));
        }
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::changeViewSort)
            .invoke(new BoardEntity.ChangeViewSort(blockId, List.copyOf(sorts), now));
      }
      if (updated.containsKey("groupById")) {
        componentClient.forEventSourcedEntity(boardId).method(BoardEntity::changeViewGroupBy)
            .invoke(new BoardEntity.ChangeViewGroupBy(
                blockId, Blocks.asString(updated.get("groupById"), null), now));
      }
      if (updated.containsKey("visiblePropertyIds")) {
        componentClient.forEventSourcedEntity(boardId)
            .method(BoardEntity::changeViewVisibleProperties)
            .invoke(new BoardEntity.ChangeViewVisibleProperties(blockId,
                Blocks.asStrings(updated.get("visiblePropertyIds")), now));
      }
      if (updated.containsKey("visibleOptionIds") || updated.containsKey("hiddenOptionIds")) {
        var view = state.views().get(blockId);
        componentClient.forEventSourcedEntity(boardId)
            .method(BoardEntity::changeViewOptionVisibility)
            .invoke(new BoardEntity.ChangeViewOptionVisibility(blockId,
                updated.containsKey("visibleOptionIds")
                    ? Blocks.asStrings(updated.get("visibleOptionIds")) : view.visibleOptionIds(),
                updated.containsKey("hiddenOptionIds")
                    ? Blocks.asStrings(updated.get("hiddenOptionIds")) : view.hiddenOptionIds(),
                now));
      }
      var after = read(boardId);
      return Blocks.viewBlock(boardId, after.views().get(blockId), after.createAt(),
          after.updateAt());
    }

    // R19, in the original's own words, so a client comparing the two reads the same string.
    throw HttpException.notFound();
  }

  // ---------------------------------------------------- the port's own routes

  /**
   * What a view shows. The original computes this in every open browser; here it is one answer
   * from the board itself (SPEC D2).
   */
  @Get("/boards/{boardId}/views/{viewId}/derived")
  public DerivedView derived(String boardId, String viewId) {
    var search = requestContext().queryParams().getString("search").orElse("");
    return notFoundOnRefusal(() -> componentClient.forEventSourcedEntity(boardId)
        .method(BoardEntity::derive).invoke(new BoardEntity.Derive(viewId, search)));
  }

  /** A drop on a column. One command, so the property and the order land together (SPEC D3). */
  @Post("/boards/{boardId}/views/{viewId}/move-to-column")
  public DerivedView moveToColumn(String boardId, String viewId,
                                  com.fasterxml.jackson.databind.JsonNode raw) {
    var body = Blocks.toMap(raw);
    return notFoundOnRefusal(() -> componentClient.forEventSourcedEntity(boardId)
        .method(BoardEntity::moveToColumn)
        .invoke(new BoardEntity.MoveToColumn(viewId, Blocks.asString(body.get("cardId"), ""),
            Blocks.asString(body.get("optionId"), ""), System.currentTimeMillis())));
  }

  /** A drop on another card. */
  @Post("/boards/{boardId}/views/{viewId}/move-onto-card")
  public DerivedView moveOntoCard(String boardId, String viewId,
                                  com.fasterxml.jackson.databind.JsonNode raw) {
    var body = Blocks.toMap(raw);
    return notFoundOnRefusal(() -> componentClient.forEventSourcedEntity(boardId)
        .method(BoardEntity::moveOntoCard)
        .invoke(new BoardEntity.MoveOntoCard(viewId, Blocks.asString(body.get("cardId"), ""),
            Blocks.asString(body.get("targetCardId"), ""), System.currentTimeMillis())));
  }

  /**
   * RENDERING.md R1 — the board as it stands is the first thing on the wire, then every change
   * to it. A client that dropped and came back is in the same position as one that has never
   * connected: it is told the current state rather than left to work out what it missed, which
   * is the recovery policy the original's own client has.
   */
  @Get("/boards/{boardId}/stream")
  public HttpResponse stream(String boardId) {
    Source<Map<String, Object>, ?> frames =
        Source.tick(Duration.ZERO, Duration.ofMillis(100), "tick")
            .takeWithin(Duration.ofMinutes(30))
            .map(tick -> read(boardId))
            .statefulMapConcat(BoardApiEndpoint::onlyWhenChanged)
            .map(BoardApiEndpoint::frame);
    return HttpResponses.serverSentEvents(frames);
  }

  /**
   * The same stream, for every board a team has.
   *
   * <p>The interface subscribes per team rather than per board -- that is what focalboard's own
   * client does, and reusing it means answering the subscription it actually makes.
   */
  @Get("/teams/{teamId}/stream")
  public HttpResponse teamStream(String teamId) {
    Source<Map<String, Object>, ?> frames =
        Source.tick(Duration.ZERO, Duration.ofMillis(100), "tick")
            .takeWithin(Duration.ofMinutes(30))
            .map(tick -> boardsOfTeamState(teamId))
            .statefulMapConcat(BoardApiEndpoint::onlyWhenBoardsChanged)
            .map(BoardApiEndpoint::frame);
    return HttpResponses.serverSentEvents(frames);
  }

  private List<BoardState> boardsOfTeamState(String teamId) {
    var out = new ArrayList<BoardState>();
    for (var row : componentClient.forView().method(BoardsByTeamView::byTeam)
        .invoke(teamId).boards()) {
      out.add(read(row.boardId()));
    }
    return List.copyOf(out);
  }

  private static akka.japi.function.Function<List<BoardState>, Iterable<List<BoardState>>>
      onlyWhenBoardsChanged() {
    var last = new ArrayList<List<BoardState>>();
    return states -> {
      if (!last.isEmpty() && last.get(0).equals(states)) {
        return List.of();
      }
      last.clear();
      last.add(states);
      return List.of(states);
    };
  }

  private static Map<String, Object> frame(List<BoardState> states) {
    var boards = new ArrayList<Map<String, Object>>();
    var blocks = new ArrayList<Map<String, Object>>();
    for (var state : states) {
      boards.add(Blocks.boardJson(state));
      blocks.addAll(Blocks.blocksOf(state));
    }
    var out = new LinkedHashMap<String, Object>();
    out.put("boards", boards);
    out.put("blocks", blocks);
    return out;
  }

  private static Map<String, Object> frame(BoardState state) {
    var out = new LinkedHashMap<String, Object>();
    out.put("board", Blocks.boardJson(state));
    out.put("blocks", Blocks.blocksOf(state));
    return out;
  }

  /**
   * A frame per change, not per tick. Without this the stream would be a poller with the
   * polling moved to the server, which satisfies the letter of R1.1 and nothing else.
   */
  private static akka.japi.function.Function<BoardState, Iterable<BoardState>> onlyWhenChanged() {
    var last = new BoardState[1];
    return state -> {
      if (state.equals(last[0])) {
        return List.of();
      }
      last[0] = state;
      return List.of(state);
    };
  }

  // ------------------------------------------------------------------ helpers

  /**
   * An entity refusing a command because the block it names is not there is a 404 to whoever
   * asked, not the 400 a component error surfaces by default. R19 fixes the status and the
   * wording for the patch route; the same answer belongs on every route that can be given a
   * block id.
   */
  private static <T> T notFoundOnRefusal(java.util.function.Supplier<T> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      var message = String.valueOf(e.getMessage());
      if (message.contains("not found")) {
        throw HttpException.error(akka.http.javadsl.model.StatusCodes.NOT_FOUND, message);
      }
      throw e;
    }
  }

  private BoardState read(String boardId) {
    return componentClient.forEventSourcedEntity(boardId).method(BoardEntity::read).invoke();
  }

  private BoardState requireBoard(String boardId) {
    var state = read(boardId);
    if (!state.exists()) {
      throw HttpException.notFound();
    }
    return state;
  }

}
