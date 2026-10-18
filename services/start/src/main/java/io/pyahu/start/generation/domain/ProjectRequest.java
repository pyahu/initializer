package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What someone asked for, validated. Build it with {@link #of}; a request that exists is a request the engine
 * can generate.
 */
public record ProjectRequest(
        String name, String group, String packageName, String description, Architecture architecture) {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "false",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "null",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "true",
            "try",
            "void",
            "volatile",
            "while",
            "_");

    public ProjectRequest {
        requireNonNull(name, "name");
        requireNonNull(group, "group");
        requireNonNull(packageName, "packageName");
        requireNonNull(description, "description");
        requireNonNull(architecture, "architecture");
    }

    /**
     * Validates the fields and fills in the defaults. A blank value counts as absent, as an empty form field does.
     *
     * @throws InvalidProjectRequest with every rejected field, never only the first
     */
    public static ProjectRequest of(
            @Nullable String name,
            @Nullable String group,
            @Nullable String packageName,
            @Nullable String description,
            @Nullable String architecture) {
        List<FieldError> errors = new ArrayList<>();
        String n = check(ProjectField.NAME, name, errors);
        String g = check(ProjectField.GROUP, group, errors);
        keywords(ProjectField.GROUP, g, errors);
        // A package derived from a rejected name or group would only repeat their errors.
        boolean derived = packageName == null || packageName.isBlank();
        String p = derived ? g + "." + n.replace("-", "") : check(ProjectField.PACKAGE, packageName, "", errors);
        if (!derived || errors.isEmpty()) {
            keywords(ProjectField.PACKAGE, p, errors);
        }
        String d = check(ProjectField.DESCRIPTION, description, errors);
        String a = check(ProjectField.ARCHITECTURE, architecture, errors);
        if (!errors.isEmpty()) {
            throw new InvalidProjectRequest(errors);
        }
        return new ProjectRequest(n, g, p, d, Architecture.of(a).orElseThrow());
    }

    /** The package as a directory, such as {@code com/example/demo}. */
    public String packagePath() {
        return packageName.replace('.', '/');
    }

    /**
     * The options in the shape the guides' checklist filters on (see CONTRIBUTING.md, "Checklist"). The base project
     * has a REST API and no database, messaging, cache or login yet.
     */
    public Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("architecture", architecture.id());
        options.put("api", List.of("rest"));
        options.put("database", "none");
        options.put("kafka.enabled", false);
        options.put("kafka.outbox", false);
        options.put("rabbitmq", false);
        options.put("cache", false);
        options.put("auth", false);
        options.put("pyahuCli", false);
        options.put("sample", true);
        return options;
    }

    private static String check(ProjectField field, @Nullable String value, List<FieldError> errors) {
        return check(field, value, requireNonNull(field.defaultValue(), field.id()), errors);
    }

    private static String check(ProjectField field, @Nullable String value, String fallback, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.strip();
        if (v.length() > field.maxLength()) {
            errors.add(new FieldError(field.id(), "at most " + field.maxLength() + " characters"));
        } else if (!field.pattern().matcher(v).matches()) {
            errors.add(
                    new FieldError(field.id(), "must match " + field.pattern().pattern()));
        }
        return v;
    }

    private static void keywords(ProjectField field, String value, List<FieldError> errors) {
        for (String segment : value.toLowerCase(Locale.ROOT).split("\\.")) {
            if (JAVA_KEYWORDS.contains(segment)) {
                errors.add(new FieldError(field.id(), "'" + segment + "' is a Java keyword"));
            }
        }
    }
}
