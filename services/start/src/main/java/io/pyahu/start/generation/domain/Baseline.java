package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The frozen list of versions a generated project uses: one revision of data/versions.yaml.
 *
 * @param id the revision, such as {@code v0.1-b1}
 * @param components every component of the revision by its key, such as {@code spring-boot}
 */
public record Baseline(String id, Map<String, Pin> components) {

    public Baseline {
        requireNonNull(id, "id");
        components = Map.copyOf(components);
    }

    /** The pin of a component the engine needs; a baseline without it cannot generate a project. */
    public Pin pin(String component) {
        Pin pin = components.get(component);
        if (pin == null) {
            throw new IllegalStateException("baseline " + id + " has no component " + component);
        }
        return pin;
    }

    public String version(String component) {
        return pin(component).version();
    }

    /** A field of a pin that must be there, such as an image digest. */
    public String required(String component, Function<Pin, @Nullable String> field, String what) {
        String value = field.apply(pin(component));
        if (value == null) {
            throw new IllegalStateException("baseline " + id + ": " + component + " has no " + what);
        }
        return value;
    }

    public String java() {
        return version("java");
    }

    public String springBoot() {
        return version("spring-boot");
    }

    /** The Java feature release a toolchain asks for, such as {@code 25}. */
    public int javaFeature() {
        return Integer.parseInt(java().split("[.+]", 2)[0]);
    }
}
