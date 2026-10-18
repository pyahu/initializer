package io.pyahu.start.generation.infrastructure;

import io.pyahu.start.generation.domain.Baseline;
import io.pyahu.start.generation.domain.Baselines;
import io.pyahu.start.generation.domain.Pin;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads the active revision of the data/versions.yaml copy the build puts in the jar. Read once, at startup. */
@Component
class ClasspathBaselines implements Baselines {

    static final String RESOURCE = "baseline/versions.yaml";

    private final Baseline active;

    ClasspathBaselines() {
        this.active = read(new ClassPathResource(RESOURCE));
    }

    @Override
    public Baseline active() {
        return active;
    }

    static Baseline read(ClassPathResource resource) {
        try (InputStream in = resource.getInputStream()) {
            Map<?, ?> root = map(new Yaml(new SafeConstructor(new LoaderOptions())).load(in), "the file");
            if (!(root.get("active") instanceof String id) || !(root.get("revisions") instanceof List<?> revisions)) {
                throw new IllegalStateException(RESOURCE + ": active and revisions are required");
            }
            for (Object r : revisions) {
                Map<?, ?> revision = map(r, "a revision");
                if (id.equals(revision.get("id"))) {
                    Map<String, Pin> pins = new LinkedHashMap<>();
                    map(revision.get("components"), "components of " + id)
                            .forEach((key, value) -> pins.put(String.valueOf(key), pin(String.valueOf(key), value)));
                    return new Baseline(id, pins);
                }
            }
            throw new IllegalStateException(RESOURCE + ": active revision " + id + " not found");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Pin pin(String key, @Nullable Object value) {
        Map<?, ?> c = map(value, key);
        if (!(c.get("version") instanceof String version)) {
            throw new IllegalStateException(RESOURCE + ": no version for " + key);
        }
        return new Pin(version, text(c, "image"), text(c, "digest"), text(c, "distributionSha256Sum"));
    }

    private static @Nullable String text(Map<?, ?> component, String field) {
        return component.get(field) instanceof String s ? s : null;
    }

    private static Map<?, ?> map(@Nullable Object value, String what) {
        if (!(value instanceof Map<?, ?> m)) {
            throw new IllegalStateException(RESOURCE + ": " + what + " is not a map");
        }
        return m;
    }
}
