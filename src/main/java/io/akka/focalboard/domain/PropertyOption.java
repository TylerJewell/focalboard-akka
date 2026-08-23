package io.akka.focalboard.domain;

/** One choice a select or multi-select property offers. A card stores the id; a person reads the value. */
public record PropertyOption(String id, String value, String color) {}
