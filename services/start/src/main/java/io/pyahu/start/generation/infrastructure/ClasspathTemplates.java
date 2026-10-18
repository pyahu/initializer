package io.pyahu.start.generation.infrastructure;

import io.pyahu.start.generation.domain.TemplateCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The templates the build packs into the jar under {@code templates/}, listed by the index the build writes next to
 * them. Read once, at startup.
 */
@Component
class ClasspathTemplates implements TemplateCatalog {

    static final String ROOT = "templates/";

    private final List<String> index;

    ClasspathTemplates() {
        this.index = new String(read("index.txt"), StandardCharsets.UTF_8)
                .lines()
                .filter(l -> !l.isBlank())
                .sorted()
                .toList();
    }

    @Override
    public List<String> files(String set) {
        String prefix = set + "/";
        List<String> files = index.stream()
                .filter(f -> f.startsWith(prefix))
                .map(f -> f.substring(prefix.length()))
                .toList();
        if (files.isEmpty()) {
            throw new IllegalStateException("no template set " + set);
        }
        return files;
    }

    /** A file under the templates root, such as {@code base/README.md}. */
    byte[] read(String path) {
        try (InputStream in = new ClassPathResource(ROOT + path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(ROOT + path, e);
        }
    }
}
