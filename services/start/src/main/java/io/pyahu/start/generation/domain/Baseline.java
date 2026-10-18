package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

/**
 * The frozen list of versions a generated project uses: one revision of data/versions.yaml.
 *
 * @param id the revision, such as {@code v0.1-b1}
 * @param java the full Java version, such as {@code 25.0.4.1+1}
 * @param springBoot the Spring Boot version, such as {@code 4.1.1}
 */
public record Baseline(String id, String java, String springBoot) {

    public Baseline {
        requireNonNull(id, "id");
        requireNonNull(java, "java");
        requireNonNull(springBoot, "springBoot");
    }

    /** The Java feature release a toolchain asks for, such as {@code 25}. */
    public int javaFeature() {
        return Integer.parseInt(java.split("[.+]", 2)[0]);
    }
}
