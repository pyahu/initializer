package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

/**
 * One service of a monorepo, in {@code services/<name>/} with its contract project in {@code api/}. Each vertical
 * picks its own architecture and database.
 *
 * @param port its local HTTP port, so several run side by side; management listens on the next one
 */
public record Vertical(String name, Architecture architecture, Database database, int port) {

    public Vertical {
        requireNonNull(name, "name");
        requireNonNull(architecture, "architecture");
        requireNonNull(database, "database");
    }
}
