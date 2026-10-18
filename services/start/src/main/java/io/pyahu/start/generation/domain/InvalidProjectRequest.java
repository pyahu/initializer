package io.pyahu.start.generation.domain;

import java.util.List;

/** A request that names unknown fields or values the rules reject. Nothing is generated. */
public class InvalidProjectRequest extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidProjectRequest(List<FieldError> errors) {
        super("Invalid project request: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
