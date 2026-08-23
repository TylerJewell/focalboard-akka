package io.akka.focalboard.domain;

import java.util.List;

/**
 * One test against one property.
 *
 * <p>Every field is a plain string or a list of them. A sealed interface would have been the
 * natural way to say "a clause or a group", and it is deliberately not used for the fields a
 * command carries: this target resolves polymorphic JSON only at the top level of a
 * persisted or transmitted type, and a two-variant interface nested inside another record's
 * field fails at runtime rather than at compile time.
 */
public record FilterClause(String propertyId, String condition, List<String> values) {}
