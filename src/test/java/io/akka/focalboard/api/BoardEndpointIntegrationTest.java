package io.akka.focalboard.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.TestKitSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SPEC R19, R20, D2, D3 over the real HTTP layer.
 *
 * <p>These go through {@code httpClient} rather than the component client on purpose. A test
 * that calls the entity directly never touches the JSON layer, so a query parameter nobody
 * read and a field that will not deserialize both pass it.
 */
public class BoardEndpointIntegrationTest extends TestKitSupport {

  private static final String STATUS = "prop-status";
  private static final String PRIORITY = "prop-priority";

  private static Map<String, Object> option(String id, String value) {
    return Map.of("id", id, "value", value, "color", "");
  }

  private String createBoard(String boardId) {
    var body = new LinkedHashMap<String, Object>();
    body.put("id", boardId);
    body.put("teamId", "0");
    body.put("title", "Port board");
    body.put("cardProperties", List.of(
        Map.of("id", STATUS, "name", "Status", "type", "select", "options",
            List.of(option("opt-todo", "To Do"), option("opt-doing", "Doing"),
                option("opt-done", "Done"))),
        Map.of("id", PRIORITY, "name", "Priority", "type", "number", "options", List.of())));

    var response = httpClient.POST("/api/v2/boards").withRequestBody(body)
        .invoke();
    assertEquals(200, response.httpResponse().status().intValue());
    return boardId;
  }

  private static Map<String, Object> cardBlock(String id, String title, long createAt,
                                               Map<String, Object> properties) {
    return Map.of("id", id, "type", "card", "title", title, "createAt", createAt,
        "updateAt", createAt, "fields", Map.of("properties", properties));
  }

  private static Map<String, Object> viewBlock(String id, List<String> cardOrder) {
    return Map.of("id", id, "type", "view", "title", id, "fields",
        Map.of("viewType", "board", "groupById", STATUS, "sortOptions", List.of(),
            "cardOrder", cardOrder, "visibleOptionIds", List.of(),
            "hiddenOptionIds", List.of(),
            "filter", Map.of("operation", "and", "filters", List.of())));
  }

  private void seedBlocks(String boardId) {
    var blocks = List.of(
        cardBlock("card-1", "Alpha", 1_700_000_000_000L,
            Map.of(STATUS, "opt-todo", PRIORITY, "3")),
        cardBlock("card-2", "Bravo", 1_700_000_001_000L,
            Map.of(STATUS, "opt-doing", PRIORITY, "1")),
        cardBlock("card-3", "Charlie", 1_700_000_002_000L,
            Map.of(STATUS, "opt-done", PRIORITY, "2")),
        cardBlock("card-4", "Delta", 1_700_000_003_000L,
            Map.of(STATUS, "opt-todo", PRIORITY, "2")),
        cardBlock("card-5", "Echo", 1_700_000_004_000L, Map.of()),
        viewBlock("view-kanban", List.of("card-1", "card-2", "card-3", "card-4", "card-5")));
    var response = httpClient.POST("/api/v2/boards/" + boardId + "/blocks")
        .withRequestBody(blocks).invoke();
    assertEquals(200, response.httpResponse().status().intValue());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> derived(String boardId, String viewId, String search) {
    var path = "/api/v2/boards/" + boardId + "/views/" + viewId + "/derived"
        + (search.isEmpty() ? "" : "?search=" + search);
    return httpClient.GET(path).responseBodyAs(Map.class).invoke().body();
  }

  @SuppressWarnings("unchecked")
  private static List<String> groupCards(Map<String, Object> derived, String which,
                                         String optionId) {
    for (var group : (List<Map<String, Object>>) derived.get(which)) {
      if (optionId.equals(group.get("optionId"))) {
        return (List<String>) group.get("cardIds");
      }
    }
    throw new AssertionError("no " + which + " group " + optionId + " in " + derived);
  }

  @Test
  public void derivesAViewOverHttp() {
    var boardId = createBoard("board-derive");
    seedBlocks(boardId);

    var answer = derived(boardId, "view-kanban", "");
    assertEquals(List.of("card-1", "card-2", "card-3", "card-4", "card-5"),
        answer.get("orderedCardIds"));
    assertEquals(List.of("card-1", "card-4"), groupCards(answer, "visible", "opt-todo"));
    assertEquals(List.of("card-5"), groupCards(answer, "visible", ""));
  }

  /**
   * The search text arrives as a query parameter, which a method signature does not bind on
   * its own. An endpoint that forgot to read it compiles, and every component-client test
   * passes, because those never go through the HTTP layer at all.
   */
  @Test
  public void readsTheSearchTextFromTheQueryString() {
    var boardId = createBoard("board-search");
    seedBlocks(boardId);
    assertEquals(List.of("card-1"), derived(boardId, "view-kanban", "al").get("orderedCardIds"));
    assertEquals(List.of("card-2"),
        derived(boardId, "view-kanban", "doing").get("orderedCardIds"));
  }

  @Test
  public void aMoveWritesBothHalvesOrNeither() {
    var boardId = createBoard("board-move");
    seedBlocks(boardId);

    var response = httpClient.POST("/api/v2/boards/" + boardId + "/views/view-kanban/move-to-column")
        .withRequestBody(Map.of("cardId", "card-1", "optionId", "opt-doing"))
        .responseBodyAs(Map.class).invoke();
    assertEquals(200, response.httpResponse().status().intValue());

    @SuppressWarnings("unchecked")
    var answer = (Map<String, Object>) response.body();
    assertEquals(List.of("card-2", "card-1"), groupCards(answer, "visible", "opt-doing"));

    // Both halves are visible in the blocks the same request left behind: the card's property
    // and the view's order.
    var blocks = blocks(boardId);
    assertEquals("opt-doing", propertyOf(blocks, "card-1", STATUS));
    assertEquals(List.of("card-2", "card-1", "card-3", "card-4", "card-5"),
        cardOrderOf(blocks, "view-kanban"));
  }

  @Test
  public void movingACardOnAViewThatIsNotThereIs404() {
    var boardId = createBoard("board-move-404");
    seedBlocks(boardId);
    var response = httpClient.POST("/api/v2/boards/" + boardId + "/views/view-nowhere/move-to-column")
        .withRequestBody(Map.of("cardId", "card-1", "optionId", "opt-doing")).invoke();
    assertEquals(404, response.httpResponse().status().intValue());

    var missingCard = httpClient.POST("/api/v2/boards/" + boardId + "/views/view-kanban/move-to-column")
        .withRequestBody(Map.of("cardId", "card-nowhere", "optionId", "opt-doing")).invoke();
    assertEquals(404, missingCard.httpResponse().status().intValue());
  }

  @Test
  public void derivingAViewThatIsNotThereIs404() {
    var boardId = createBoard("board-derive-404");
    seedBlocks(boardId);
    var response = httpClient.GET(
        "/api/v2/boards/" + boardId + "/views/view-nowhere/derived").invoke();
    assertEquals(404, response.httpResponse().status().intValue());
  }

  @Test
  public void aBulkInsertLargerThanOneCommandStillLands() {
    // G1: the runtime refuses a command whose payload exceeds 1 MB outright. 2,500 cards is
    // past the endpoint's chunk size, so this fails if the chunking is removed.
    var boardId = createBoard("board-bulk");
    var blocks = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 2500; i++) {
      blocks.add(cardBlock("bulk-" + i, "Card " + i, 1_700_000_000_000L + i,
          Map.of(STATUS, "opt-todo")));
    }
    blocks.add(viewBlock("view-kanban", List.of()));
    var response = httpClient.POST("/api/v2/boards/" + boardId + "/blocks")
        .withRequestBody(blocks).invoke();
    assertEquals(200, response.httpResponse().status().intValue());
    assertEquals(2501, blocks(boardId).size());
  }

  @Test
  public void patchingAnAbsentBlockIs404() {
    var boardId = createBoard("board-404");
    seedBlocks(boardId);
    var response = httpClient.PATCH("/api/v2/boards/" + boardId + "/blocks/card-nowhere")
        .withRequestBody(Map.of("updatedFields", Map.of("properties", Map.of())))
        .invoke();
    assertEquals(404, response.httpResponse().status().intValue());
  }

  @Test
  public void storesAPropertyValueThatNamesNoOption() {
    var boardId = createBoard("board-stray");
    seedBlocks(boardId);
    var response = httpClient.PATCH("/api/v2/boards/" + boardId + "/blocks/card-1")
        .withRequestBody(Map.of("updatedFields",
            Map.of("properties", Map.of(STATUS, "opt-that-was-deleted"))))
        .invoke();
    assertEquals(200, response.httpResponse().status().intValue());

    assertEquals("opt-that-was-deleted", propertyOf(blocks(boardId), "card-1", STATUS));
    // R10: a value naming no option puts the card in the empty column.
    assertTrue(groupCards(derived(boardId, "view-kanban", ""), "visible", "")
        .contains("card-1"));
  }

  @Test
  public void patchingOnePropertyReplacesTheWholeMap() {
    var boardId = createBoard("board-patch");
    seedBlocks(boardId);
    httpClient.PATCH("/api/v2/boards/" + boardId + "/blocks/card-1")
        .withRequestBody(Map.of("updatedFields",
            Map.of("properties", Map.of(STATUS, "opt-doing"))))
        .invoke();

    var blocks = blocks(boardId);
    assertEquals("opt-doing", propertyOf(blocks, "card-1", STATUS));
    assertEquals(null, propertyOf(blocks, "card-1", PRIORITY));
    assertEquals("Alpha", titleOf(blocks, "card-1"), "an unnamed field is untouched");
  }

  @Test
  public void aMultiValuedPropertyKeepsItsListShapeAcrossTheWire() {
    var boardId = createBoard("board-multi");
    seedBlocks(boardId);
    httpClient.PATCH("/api/v2/boards/" + boardId + "/blocks/card-1")
        .withRequestBody(Map.of("updatedFields",
            Map.of("properties", Map.of("prop-tags", List.of("tag-a", "tag-b")))))
        .invoke();

    var value = propertyOfRaw(blocks(boardId), "card-1", "prop-tags");
    assertTrue(value instanceof List, "a list must not arrive back as a string: " + value);
    assertEquals(List.of("tag-a", "tag-b"), value);
  }

  @Test
  public void aBoardShowsUpOnItsTeamsList() {
    var boardId = createBoard("board-listed");
    seedBlocks(boardId);
    org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      var boards = httpClient.GET("/api/v2/teams/0/boards").responseBodyAs(List.class)
          .invoke().body();
      var ids = new ArrayList<String>();
      for (var raw : boards) {
        ids.add(String.valueOf(((Map<?, ?>) raw).get("id")));
      }
      assertTrue(ids.contains(boardId), "expected " + boardId + " among " + ids);
    });
  }

  @Test
  public void theShellAnswersEverythingTheBoardPageAsksFor() {
    for (var path : List.of("/api/v2/clientConfig", "/api/v2/users/me", "/api/v2/users/me/config",
        "/api/v2/users/me/memberships", "/api/v2/teams", "/api/v2/teams/0",
        "/api/v2/teams/0/categories", "/api/v2/teams/0/templates", "/api/v2/limits")) {
      var response = httpClient.GET(path).invoke();
      assertEquals(200, response.httpResponse().status().intValue(), path + " answered");
    }
  }

  // ------------------------------------------------------------------ helpers

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> blocks(String boardId) {
    return httpClient.GET("/api/v2/boards/" + boardId + "/blocks")
        .responseBodyAs(List.class).invoke().body();
  }

  private static Map<String, Object> block(List<Map<String, Object>> blocks, String id) {
    for (var block : blocks) {
      if (id.equals(block.get("id"))) {
        return block;
      }
    }
    throw new AssertionError("no block " + id);
  }

  @SuppressWarnings("unchecked")
  private static Object propertyOfRaw(List<Map<String, Object>> blocks, String cardId,
                                      String propertyId) {
    var fields = (Map<String, Object>) block(blocks, cardId).get("fields");
    return ((Map<String, Object>) fields.get("properties")).get(propertyId);
  }

  private static String propertyOf(List<Map<String, Object>> blocks, String cardId,
                                   String propertyId) {
    var value = propertyOfRaw(blocks, cardId, propertyId);
    return value == null ? null : String.valueOf(value);
  }

  private static String titleOf(List<Map<String, Object>> blocks, String cardId) {
    return String.valueOf(block(blocks, cardId).get("title"));
  }

  @SuppressWarnings("unchecked")
  private static List<String> cardOrderOf(List<Map<String, Object>> blocks, String viewId) {
    var fields = (Map<String, Object>) block(blocks, viewId).get("fields");
    return (List<String>) fields.get("cardOrder");
  }
}
