package io.akka.focalboard.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SPEC R6: every filter condition, against every state a value can be in, answered the way
 * the original answers it.
 *
 * <p>The expected answers are not written here. They were recorded by running focalboard's
 * own `CardFilter.isClauseMet` over 750 cases
 * (`focalboard-port/probes/source_probe/probe_conditions.ts`) and are replayed from
 * `src/test/resources/source-filter-conditions.json`. A table of 750 booleans transcribed by
 * hand is a table nobody checks.
 */
public class CardFilterConditionsTest {

  private static final List<PropertyTemplate> TEMPLATES = List.of(
      new PropertyTemplate("p-select", "Status", "select",
          List.of(new PropertyOption("opt-todo", "To Do", ""),
                  new PropertyOption("opt-done", "Done", ""))),
      new PropertyTemplate("p-multi", "Tags", "multiSelect",
          List.of(new PropertyOption("tag-a", "A", ""), new PropertyOption("tag-b", "B", ""))),
      new PropertyTemplate("p-text", "Notes", "text", List.of()),
      new PropertyTemplate("p-number", "Priority", "number", List.of()));

  /** The four conditions the original throws on for a list value; SPEC D1 gives the port a rule. */
  private static final List<String> AFFIX =
      List.of("startsWith", "notStartsWith", "endsWith", "notEndsWith");

  @Test
  public void matchesTheOriginalOnEveryEnumeratedCase() throws IOException {
    var cases = new ObjectMapper().readTree(
        getClass().getResourceAsStream("/source-filter-conditions.json"));

    var disagreements = new ArrayList<String>();
    int compared = 0;
    int declared = 0;

    for (JsonNode node : cases) {
      var condition = node.get("condition").asText();
      if (node.get("threw").asBoolean()) {
        // Every throw the original produces is one of the four affix conditions on a list
        // value, which is exactly what D1 covers. Asserting that keeps the exclusion from
        // quietly widening to cover a case nobody looked at.
        assertTrue(AFFIX.contains(condition),
            "the original threw on a case D1 does not cover: " + node.get("name").asText());
        declared++;
        continue;
      }

      var card = card(node.get("propertyId").asText(), node.get("storedValue"));
      var values = new ArrayList<String>();
      node.get("filterValues").forEach(v -> values.add(v.asText()));
      var clause = new FilterClause(node.get("propertyId").asText(), condition, values);

      var ours = CardFilter.isClauseMet(clause, TEMPLATES, card);
      var theirs = node.get("answer").asBoolean();
      compared++;
      if (ours != theirs) {
        disagreements.add(node.get("name").asText() + ": original=" + theirs + " port=" + ours);
      }
    }

    assertTrue(compared > 700, "expected the whole enumeration, compared " + compared);
    assertTrue(declared == 32,
        "the original throws on 32 of these cases; found " + declared);
    assertTrue(disagreements.isEmpty(),
        compared + " cases compared, " + disagreements.size() + " disagree:\n  "
            + String.join("\n  ", disagreements));
  }

  private static Card card(String propertyId, JsonNode stored) {
    if (stored.isNull()) {
      return new Card("card-1", "Alpha", 1_700_000_000_000L, 1_700_000_000_000L, Map.of());
    }
    PropertyValue value;
    if (stored.isArray()) {
      var items = new ArrayList<String>();
      stored.forEach(v -> items.add(v.asText()));
      value = PropertyValue.ofList(items);
    } else {
      value = PropertyValue.of(stored.asText());
    }
    return new Card("card-1", "Alpha", 1_700_000_000_000L, 1_700_000_000_000L,
        Map.of(propertyId, value));
  }
}
