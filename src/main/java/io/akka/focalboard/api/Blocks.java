package io.akka.focalboard.api;

import io.akka.focalboard.application.BoardState;
import io.akka.focalboard.domain.Card;
import io.akka.focalboard.domain.FilterClause;
import io.akka.focalboard.domain.FilterGroup;
import io.akka.focalboard.domain.PropertyOption;
import io.akka.focalboard.domain.PropertyTemplate;
import io.akka.focalboard.domain.PropertyValue;
import io.akka.focalboard.domain.SortOption;
import io.akka.focalboard.domain.ViewSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translation between focalboard's block JSON and this port's domain.
 *
 * <p>The wire shape is the original's, unchanged, because the interface reading it is the
 * original's. Everything the port decided differently is behind this line, not on it.
 *
 * <p>A property value arrives as either a string or a list of strings, and the two are kept
 * apart all the way down: an empty list and an empty string answer two filter conditions
 * differently, so collapsing them here would decide those rules by accident.
 */
public final class Blocks {

  public static final String SYSTEM_USER = "focalboard-akka";

  private Blocks() {}

  private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();

  /**
   * The endpoint layer refuses a generic {@code Map<String, Object>} as a request body type,
   * so a request body arrives as a tree and is converted here. Doing it in one place keeps the
   * rest of this class reading one shape.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> toMap(com.fasterxml.jackson.databind.JsonNode node) {
    return node == null || node.isNull() ? Map.of() : MAPPER.convertValue(node, Map.class);
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> toMaps(com.fasterxml.jackson.databind.JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    var out = new ArrayList<Map<String, Object>>();
    node.forEach(item -> out.add(toMap(item)));
    return List.copyOf(out);
  }

  // ------------------------------------------------------------------ reading

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : Map.of();
  }

  @SuppressWarnings("unchecked")
  public static List<Object> asList(Object value) {
    return value instanceof List ? (List<Object>) value : List.of();
  }

  public static String asString(Object value, String fallback) {
    return value instanceof String s ? s : fallback;
  }

  public static List<String> asStrings(Object value) {
    var out = new ArrayList<String>();
    for (var item : asList(value)) {
      if (item instanceof String s) {
        out.add(s);
      }
    }
    return List.copyOf(out);
  }

  public static long asLong(Object value, long fallback) {
    if (value instanceof Number n) {
      return n.longValue();
    }
    return fallback;
  }

  public static PropertyValue propertyValue(Object raw) {
    if (raw instanceof List<?>) {
      return PropertyValue.ofList(asStrings(raw));
    }
    if (raw instanceof Boolean b) {
      return PropertyValue.of(b ? "true" : "");
    }
    if (raw instanceof Number n) {
      return PropertyValue.of(n.toString());
    }
    return PropertyValue.of(raw == null ? "" : raw.toString());
  }

  public static Object propertyJson(PropertyValue value) {
    return value.multi() ? value.values() : value.asText();
  }

  public static Map<String, PropertyValue> properties(Object raw) {
    var out = new LinkedHashMap<String, PropertyValue>();
    asMap(raw).forEach((key, value) -> out.put(key, propertyValue(value)));
    return out;
  }

  public static PropertyTemplate propertyTemplate(Object raw) {
    var map = asMap(raw);
    var options = new ArrayList<PropertyOption>();
    for (var option : asList(map.get("options"))) {
      var o = asMap(option);
      options.add(new PropertyOption(asString(o.get("id"), ""), asString(o.get("value"), ""),
          asString(o.get("color"), "")));
    }
    return new PropertyTemplate(asString(map.get("id"), ""), asString(map.get("name"), ""),
        asString(map.get("type"), "text"), List.copyOf(options));
  }

  public static FilterGroup filterGroup(Object raw) {
    var map = asMap(raw);
    if (map.isEmpty()) {
      return FilterGroup.empty();
    }
    var clauses = new ArrayList<FilterClause>();
    var groups = new ArrayList<FilterGroup>();
    for (var filter : asList(map.get("filters"))) {
      var f = asMap(filter);
      if (f.containsKey("operation")) {
        groups.add(filterGroup(f));
      } else {
        clauses.add(new FilterClause(asString(f.get("propertyId"), ""),
            asString(f.get("condition"), "includes"), asStrings(f.get("values"))));
      }
    }
    return new FilterGroup(asString(map.get("operation"), FilterGroup.AND),
        List.copyOf(clauses), List.copyOf(groups));
  }

  public static Card card(Map<String, Object> block, long now) {
    var fields = asMap(block.get("fields"));
    var createAt = asLong(block.get("createAt"), now);
    return new Card(asString(block.get("id"), ""), asString(block.get("title"), ""),
        createAt, asLong(block.get("updateAt"), createAt), properties(fields.get("properties")));
  }

  public static ViewSpec view(Map<String, Object> block) {
    var fields = asMap(block.get("fields"));
    var sorts = new ArrayList<SortOption>();
    for (var sort : asList(fields.get("sortOptions"))) {
      var s = asMap(sort);
      sorts.add(new SortOption(asString(s.get("propertyId"), ""),
          Boolean.TRUE.equals(s.get("reversed"))));
    }
    return new ViewSpec(asString(block.get("id"), ""), asString(block.get("title"), ""),
        asString(fields.get("viewType"), "board"),
        fields.get("groupById") instanceof String g && !g.isEmpty() ? g : null,
        List.copyOf(sorts), filterGroup(fields.get("filter")),
        asStrings(fields.get("cardOrder")), asStrings(fields.get("visibleOptionIds")),
        asStrings(fields.get("hiddenOptionIds")), asStrings(fields.get("visiblePropertyIds")));
  }

  // ------------------------------------------------------------------ writing

  public static Map<String, Object> cardBlock(String boardId, Card card) {
    var properties = new LinkedHashMap<String, Object>();
    card.properties().forEach((key, value) -> properties.put(key, propertyJson(value)));
    return block(boardId, card.id(), "card", card.title(), card.createAt(), card.updateAt(),
        Map.of("properties", properties, "contentOrder", List.of(), "icon", ""));
  }

  public static Map<String, Object> viewBlock(String boardId, ViewSpec view, long createAt,
                                              long updateAt) {
    var sorts = new ArrayList<Map<String, Object>>();
    for (var sort : view.sortOptions()) {
      sorts.add(Map.of("propertyId", sort.propertyId(), "reversed", sort.reversed()));
    }
    var fields = new LinkedHashMap<String, Object>();
    fields.put("viewType", view.viewType());
    fields.put("groupById", view.groupById() == null ? "" : view.groupById());
    fields.put("sortOptions", sorts);
    fields.put("filter", filterJson(view.filter()));
    fields.put("cardOrder", view.cardOrder());
    fields.put("visibleOptionIds", view.visibleOptionIds());
    fields.put("hiddenOptionIds", view.hiddenOptionIds());
    fields.put("collapsedOptionIds", List.of());
    fields.put("visiblePropertyIds", view.visiblePropertyIds());
    fields.put("columnWidths", Map.of());
    fields.put("columnCalculations", Map.of());
    fields.put("kanbanCalculations", Map.of());
    fields.put("defaultTemplateId", "");
    return block(boardId, view.id(), "view", view.title(), createAt, updateAt, fields);
  }

  private static Map<String, Object> filterJson(FilterGroup group) {
    var filters = new ArrayList<Object>();
    for (var clause : group.clauses()) {
      filters.add(Map.of("propertyId", clause.propertyId(), "condition", clause.condition(),
          "values", clause.values()));
    }
    for (var nested : group.groups()) {
      filters.add(filterJson(nested));
    }
    return Map.of("operation", group.operation(), "filters", filters);
  }

  private static Map<String, Object> block(String boardId, String id, String type, String title,
                                           long createAt, long updateAt,
                                           Map<String, Object> fields) {
    var out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    out.put("parentId", boardId);
    out.put("boardId", boardId);
    out.put("createdBy", SYSTEM_USER);
    out.put("modifiedBy", SYSTEM_USER);
    out.put("schema", 1);
    out.put("type", type);
    out.put("title", title);
    out.put("fields", fields);
    out.put("createAt", createAt);
    out.put("updateAt", updateAt);
    out.put("deleteAt", 0);
    out.put("limited", false);
    return out;
  }

  public static Map<String, Object> boardJson(BoardState state) {
    var properties = new ArrayList<Map<String, Object>>();
    for (var template : state.cardProperties()) {
      var options = new ArrayList<Map<String, Object>>();
      for (var option : template.options()) {
        options.add(Map.of("id", option.id(), "value", option.value(), "color", option.color()));
      }
      properties.add(Map.of("id", template.id(), "name", template.name(),
          "type", template.type(), "options", options));
    }
    var out = new LinkedHashMap<String, Object>();
    out.put("id", state.boardId());
    out.put("teamId", state.teamId());
    out.put("channelId", "");
    out.put("createdBy", SYSTEM_USER);
    out.put("modifiedBy", SYSTEM_USER);
    out.put("type", "P");
    out.put("minimumRole", "");
    out.put("title", state.title());
    out.put("description", "");
    out.put("icon", "");
    out.put("showDescription", false);
    out.put("isTemplate", false);
    out.put("templateVersion", 0);
    out.put("properties", Map.of());
    out.put("cardProperties", properties);
    out.put("createAt", state.createAt());
    out.put("updateAt", state.updateAt());
    out.put("deleteAt", 0);
    return out;
  }

  public static List<Map<String, Object>> blocksOf(BoardState state) {
    var out = new ArrayList<Map<String, Object>>();
    for (var card : state.cards().values()) {
      out.add(cardBlock(state.boardId(), card));
    }
    for (var view : state.views().values()) {
      out.add(viewBlock(state.boardId(), view, state.createAt(), state.updateAt()));
    }
    return List.copyOf(out);
  }
}
