package io.pyahu.start.generation.domain;

import java.util.Map;

/** What someone asked for, validated: one service or a monorepo of verticals. */
public sealed interface GenerationRequest permits ProjectRequest, MonorepoRequest {

    /** The project's name, which is also the folder of the zip. */
    String name();

    /** The options in the shape the guides' checklist filters on (see CONTRIBUTING.md, "Checklist"). */
    Map<String, Object> options();

    /**
     * Validates the fields by layout.
     *
     * @throws InvalidProjectRequest with every rejected field
     */
    static GenerationRequest of(Map<String, String> fields) {
        String layout = fields.getOrDefault(ProjectField.LAYOUT.id(), "");
        if (layout.isBlank() || layout.strip().equals(Layout.SINGLE.id())) {
            return ProjectRequest.of(fields);
        }
        if (layout.strip().equals(Layout.MONOREPO.id())) {
            return MonorepoRequest.of(fields);
        }
        throw new InvalidProjectRequest(
                java.util.List.of(new FieldError(ProjectField.LAYOUT.id(), "must be one of single, monorepo")));
    }
}
