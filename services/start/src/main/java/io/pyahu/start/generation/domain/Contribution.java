package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;

/**
 * A typed piece of a generated project. Each one has a key; two contributions with the same key conflict (see
 * {@link ProjectDescription#add}).
 */
public sealed interface Contribution {

    String key();

    /**
     * A file rendered from a template.
     *
     * @param path where it lands in the project
     * @param template the template, as {@code <set>/<path in the set>}
     */
    record File(String path, String template, boolean executable) implements Contribution {

        public File {
            requireNonNull(path, "path");
            requireNonNull(template, "template");
        }

        @Override
        public String key() {
            return "file " + path;
        }
    }

    /** Where a dependency goes in the build file; sections are written in this order. */
    enum Section {
        PLATFORMS,
        MAIN,
        DEVELOPMENT,
        ANALYSIS,
        TEST
    }

    /** A line of the build's dependencies block, such as {@code implementation(libs.jspecify)}. */
    record Dependency(Section section, String configuration, String notation) implements Contribution {

        public Dependency {
            requireNonNull(section, "section");
            requireNonNull(configuration, "configuration");
            requireNonNull(notation, "notation");
        }

        @Override
        public String key() {
            return "dependency " + configuration + " " + notation;
        }
    }

    /** A version in the catalog's {@code [versions]}. */
    record CatalogVersion(String alias, String version) implements Contribution {

        public CatalogVersion {
            requireNonNull(alias, "alias");
            requireNonNull(version, "version");
        }

        @Override
        public String key() {
            return "catalog version " + alias;
        }
    }

    /** A library in the catalog; without a version reference, a BOM sets the version. */
    record CatalogLibrary(
            String alias, String module, @Nullable String versionRef) implements Contribution {

        public CatalogLibrary {
            requireNonNull(alias, "alias");
            requireNonNull(module, "module");
        }

        @Override
        public String key() {
            return "catalog library " + alias;
        }
    }

    /** A plugin in the catalog, applied in the build file. */
    record CatalogPlugin(String alias, String id, String versionRef) implements Contribution {

        public CatalogPlugin {
            requireNonNull(alias, "alias");
            requireNonNull(id, "id");
            requireNonNull(versionRef, "versionRef");
        }

        @Override
        public String key() {
            return "catalog plugin " + alias;
        }

        /** How the build file names it, such as {@code spring.boot}. */
        public String accessor() {
            return alias.replace('-', '.');
        }
    }

    /**
     * A key of application.yaml. Segments are separated by dots; a segment in brackets keeps its dots, as in
     * {@code management.opentelemetry.resource-attributes.[service.version]}.
     *
     * @param value the YAML scalar, written as is
     */
    record Property(String name, String value) implements Contribution {

        public Property {
            requireNonNull(name, "name");
            requireNonNull(value, "value");
        }

        @Override
        public String key() {
            return "property " + name;
        }
    }

    /** A named value the templates use, such as the package of the generated API. */
    record Value(String name, String value) implements Contribution {

        public Value {
            requireNonNull(name, "name");
            requireNonNull(value, "value");
        }

        @Override
        public String key() {
            return "value " + name;
        }
    }

    /** An item of the checklist that ships with the project. */
    record ChecklistEntry(ChecklistItem item) implements Contribution {

        public ChecklistEntry {
            requireNonNull(item, "item");
        }

        @Override
        public String key() {
            return "checklist " + item.id();
        }
    }
}
