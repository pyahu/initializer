package io.pyahu.start.generation.domain;

/** Why one field of a request was rejected. */
public record FieldError(String field, String message) {}
