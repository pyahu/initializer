package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

/** A generated project, as a zip file. */
public record ProjectArchive(String fileName, byte[] content) {

    public ProjectArchive {
        requireNonNull(fileName, "fileName");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
