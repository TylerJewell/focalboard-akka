package io.akka.focalboard.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.TestKitSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * RENDERING.md R1, over the real stream.
 *
 * <p>Two things are checked here that no unit test reaches: that the board's current state is
 * the first thing on the wire, so a client that has just connected needs no second request to
 * fetch it (R1.4), and that a change made afterwards arrives without the client asking (R1.1).
 * How the browser recovers when the connection is cut is R1.3, and that is checked against the
 * real interface in {@code focalboard-port/gui/}, not here — a test that never opens a socket
 * cannot show a socket being closed.
 */
public class BoardStreamIntegrationTest extends TestKitSupport {

  private static final Duration WAIT = Duration.ofSeconds(20);
  private static final String STATUS = "prop-status";

  private void seed(String boardId) {
    httpClient.POST("/api/v2/boards").withRequestBody(Map.of(
        "id", boardId, "teamId", "0", "title", "Streamed board",
        "cardProperties", List.of(Map.of("id", STATUS, "name", "Status", "type", "select",
            "options", List.of(Map.of("id", "opt-todo", "value", "To Do", "color", ""),
                Map.of("id", "opt-doing", "value", "Doing", "color", "")))))).invoke();

    httpClient.POST("/api/v2/boards/" + boardId + "/blocks").withRequestBody(List.of(
        Map.of("id", "card-1", "type", "card", "title", "Alpha", "createAt", 1_700_000_000_000L,
            "fields", Map.of("properties", Map.of(STATUS, "opt-todo"))),
        Map.of("id", "view-1", "type", "view", "title", "By status", "fields",
            Map.of("viewType", "board", "groupById", STATUS, "sortOptions", List.of(),
                "cardOrder", List.of("card-1"), "visibleOptionIds", List.of(),
                "hiddenOptionIds", List.of(),
                "filter", Map.of("operation", "and", "filters", List.of()))))).invoke();
  }

  @Test
  public void streamsBoardChanges() throws Exception {
    var boardId = "board-stream";
    seed(boardId);

    // Two frames: the state as it stands, then the state after the move. Collected on another
    // thread so the move happens while the stream is open, which is the only arrangement that
    // shows a change arriving rather than a state being fetched.
    var frames = CompletableFuture.supplyAsync(() ->
        testKit.getSelfSseRouteTester()
            .receiveFirstN("/api/v2/boards/" + boardId + "/stream", 2, WAIT));

    Thread.sleep(500);
    httpClient.POST("/api/v2/boards/" + boardId + "/views/view-1/move-to-column")
        .withRequestBody(Map.of("cardId", "card-1", "optionId", "opt-doing")).invoke();

    var received = frames.get(30, java.util.concurrent.TimeUnit.SECONDS);
    assertEquals(2, received.size(), "the current state, then the change");

    var first = received.get(0).getData();
    var second = received.get(1).getData();
    assertTrue(first.contains("\"card-1\""), "R1.4: the first frame already carries the board");
    assertTrue(first.contains("opt-todo"));
    assertTrue(second.contains("opt-doing"),
        "R1.1: the change arrived without the client asking again");
  }

  @Test
  public void aFrameIsSentPerChangeNotPerTick() throws Exception {
    var boardId = "board-quiet";
    seed(boardId);

    var frames = CompletableFuture.supplyAsync(() ->
        testKit.getSelfSseRouteTester()
            .receiveFirstN("/api/v2/boards/" + boardId + "/stream", 2, Duration.ofSeconds(3)));

    // Nothing is changed for three seconds. A stream that emitted per tick would deliver
    // dozens of frames in that window, which satisfies R1.1's letter -- the client asks for
    // nothing -- while being a poller with the polling moved to the server.
    List<akka.http.javadsl.model.sse.ServerSentEvent> received;
    try {
      received = frames.get(10, java.util.concurrent.TimeUnit.SECONDS);
    } catch (Exception timedOut) {
      received = List.of();
    }
    assertTrue(received.size() <= 1,
        "expected at most the opening frame in a quiet window, got " + received.size());
  }

  @Test
  public void aReconnectingClientIsToldTheCurrentStateRatherThanTheDifference() throws Exception {
    var boardId = "board-rejoin";
    seed(boardId);

    httpClient.POST("/api/v2/boards/" + boardId + "/views/view-1/move-to-column")
        .withRequestBody(Map.of("cardId", "card-1", "optionId", "opt-doing")).invoke();

    // A second connection, opened after the change, sees the change in its first frame. This
    // is the recovery policy the original's own client has -- on reconnect it re-reads the
    // board -- and it is what makes a dropped connection recoverable at all.
    var frames = testKit.getSelfSseRouteTester()
        .receiveFirstN("/api/v2/boards/" + boardId + "/stream", 1, WAIT);
    assertEquals(1, frames.size());
    assertTrue(frames.get(0).getData().contains("opt-doing"));
  }

  @Test
  public void theFirstFrameCarriesEnoughToDrawTheBoard() {
    var boardId = "board-first-render";
    seed(boardId);
    var frames = testKit.getSelfSseRouteTester()
        .receiveFirstN("/api/v2/boards/" + boardId + "/stream", 1, WAIT);
    var data = frames.get(0).getData();
    for (var required : new ArrayList<>(List.of("cardProperties", "\"blocks\"", "\"board\"",
        "cardOrder", "view-1", "card-1"))) {
      assertTrue(data.contains(required), "the first frame is missing " + required);
    }
  }
}
