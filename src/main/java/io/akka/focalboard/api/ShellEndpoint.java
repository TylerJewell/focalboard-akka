package io.akka.focalboard.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.focalboard.application.BoardsByTeamView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The eleven endpoints the board page needs before it will draw anything, none of which carry
 * this slice's state.
 *
 * <p>Loading the original's board page with the network log open produced thirteen distinct
 * calls. Two carry cards, views and property definitions and are rebuilt in {@link
 * BoardApiEndpoint}; these eleven are teams, users, permissions, categories, templates and
 * plan limits — all of which `focalboard-port/docs/scope.md` puts outside the slice.
 *
 * <p>Their shapes were captured from the running original once, with its identifiers
 * replaced, and are declared in `ACKNOWLEDGEMENTS.md` as what they are. Rebuilding them would
 * be rebuilding a user directory and an access model in order to compare a sort.
 */
@HttpEndpoint("/api/v2")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ShellEndpoint {

  /** The single user every request is attributed to. There is no directory here to consult. */
  public static final String USER_ID = "focalboard-akka-user";

  public static final String TEAM_ID = "0";

  private final ComponentClient componentClient;

  public ShellEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/clientConfig")
  public Map<String, Object> clientConfig() {
    var out = new LinkedHashMap<String, Object>();
    out.put("telemetry", false);
    out.put("telemetryid", "");
    out.put("enablePublicSharedBoards", false);
    out.put("teammateNameDisplay", "username");
    out.put("featureFlags", Map.of());
    out.put("maxFileSize", 0);
    return out;
  }

  @Get("/users/me")
  public Map<String, Object> me() {
    var out = new LinkedHashMap<String, Object>();
    out.put("id", USER_ID);
    out.put("username", "focalboard");
    out.put("nickname", "");
    out.put("firstname", "");
    out.put("lastname", "");
    out.put("create_at", 0);
    out.put("update_at", 0);
    out.put("delete_at", 0);
    out.put("is_bot", false);
    out.put("is_guest", false);
    out.put("roles", "system_user");
    out.put("permissions", List.of());
    return out;
  }

  @Get("/users/me/config")
  public List<Object> myConfig() {
    return List.of();
  }

  /** Every board is this user's, because there is one user. */
  @Get("/users/me/memberships")
  public List<Map<String, Object>> memberships() {
    var out = new ArrayList<Map<String, Object>>();
    for (var row : componentClient.forView().method(BoardsByTeamView::all).invoke().boards()) {
      out.add(member(row.boardId()));
    }
    return List.copyOf(out);
  }

  @Post("/users")
  public List<Map<String, Object>> usersByIds(com.fasterxml.jackson.databind.JsonNode ids) {
    return List.of(me());
  }

  @Get("/teams")
  public List<Map<String, Object>> teams() {
    return List.of(team(TEAM_ID));
  }

  @Get("/teams/{teamId}")
  public Map<String, Object> team(String teamId) {
    var out = new LinkedHashMap<String, Object>();
    out.put("id", teamId);
    out.put("title", "");
    out.put("signupToken", "");
    out.put("settings", Map.of());
    out.put("modifiedBy", "");
    out.put("updateAt", 0);
    return out;
  }

  /**
   * The one category the original creates for a new user, holding every board it can see.
   *
   * <p>The sidebar draws its board list from this rather than from the boards themselves, so
   * an empty answer here is an empty sidebar beside a full board.
   */
  @Get("/teams/{teamId}/categories")
  public List<Map<String, Object>> categories(String teamId) {
    var boards = new ArrayList<Map<String, Object>>();
    for (var row : componentClient.forView().method(BoardsByTeamView::byTeam)
        .invoke(teamId).boards()) {
      boards.add(Map.of("boardID", row.boardId(), "hidden", false));
    }
    var category = new LinkedHashMap<String, Object>();
    category.put("id", "category-boards");
    category.put("name", "Boards");
    category.put("userID", USER_ID);
    category.put("teamID", teamId);
    category.put("createAt", 0);
    category.put("updateAt", 0);
    category.put("deleteAt", 0);
    category.put("collapsed", false);
    category.put("sorting", "");
    category.put("type", "system");
    category.put("boardMetadata", boards);
    category.put("sortOrder", 0);
    return List.of(category);
  }

  @Get("/teams/{teamId}/templates")
  public List<Map<String, Object>> templates(String teamId) {
    return List.of();
  }

  @Get("/limits")
  public Map<String, Object> limits() {
    var out = new LinkedHashMap<String, Object>();
    out.put("cards", 0);
    out.put("used_cards", 0);
    out.put("card_limit_timestamp", 0);
    out.put("views", 0);
    return out;
  }

  @Get("/boards/{boardId}/members")
  public List<Map<String, Object>> boardMembers(String boardId) {
    return List.of(member(boardId));
  }

  private static Map<String, Object> member(String boardId) {
    var out = new LinkedHashMap<String, Object>();
    out.put("boardId", boardId);
    out.put("userId", USER_ID);
    out.put("roles", "");
    out.put("minimumRole", "");
    out.put("schemeAdmin", true);
    out.put("schemeEditor", true);
    out.put("schemeCommenter", false);
    out.put("schemeViewer", false);
    out.put("synthetic", false);
    return out;
  }
}
