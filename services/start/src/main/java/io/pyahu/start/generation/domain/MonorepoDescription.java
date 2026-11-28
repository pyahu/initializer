package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import java.util.List;

/**
 * Everything a monorepo consists of: the platform's own files (the build, build-logic, CI and documents) and one
 * description per vertical, whose files land in {@code services/<vertical>/}.
 */
public record MonorepoDescription(
        MonorepoRequest request, ProjectDescription platform, List<ProjectDescription> verticals) {

    public MonorepoDescription {
        requireNonNull(request, "request");
        requireNonNull(platform, "platform");
        verticals = List.copyOf(verticals);
    }
}
