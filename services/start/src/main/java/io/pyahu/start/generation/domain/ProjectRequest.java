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
        String name,
        String group,
        String packageName,
        String description,
        Architecture architecture,
        Database database,
        Auth auth,
        Frontend frontend) {

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
        requireNonNull(database, "database");
        requireNonNull(auth, "auth");
        requireNonNull(frontend, "frontend");
    }

    /**
     * Validates the fields and fills in the defaults. A blank value counts as absent, as an empty form field does.
     *
     * @param fields values by {@link ProjectField#id()}; an absent field takes its default
     * @throws InvalidProjectRequest with every rejected field, never only the first
     */
    public static ProjectRequest of(Map<String, String> fields) {
        List<FieldError> errors = new ArrayList<>();
        String n = check(ProjectField.NAME, fields, errors);
        String g = check(ProjectField.GROUP, fields, errors);
        keywords(ProjectField.GROUP, g, errors);
        // A package derived from a rejected name or group would only repeat their errors.
        String packageName = fields.get(ProjectField.PACKAGE.id());
        boolean derived = packageName == null || packageName.isBlank();
        String p = derived ? g + "." + n.replace("-", "") : check(ProjectField.PACKAGE, packageName, "", errors);
        if (!derived || errors.isEmpty()) {
            keywords(ProjectField.PACKAGE, p, errors);
        }
        String d = check(ProjectField.DESCRIPTION, fields, errors);
        String a = check(ProjectField.ARCHITECTURE, fields, errors);
        String db = check(ProjectField.DATABASE, fields, errors);
        String auth = check(ProjectField.AUTH, fields, errors);
        String f = check(ProjectField.FRONTEND, fields, errors);
        if (!errors.isEmpty()) {
            throw new InvalidProjectRequest(errors);
        }
        return new ProjectRequest(
                n,
                g,
                p,
                d,
                Choice.of(Architecture.class, a).orElseThrow(),
                Choice.of(Database.class, db).orElseThrow(),
                Choice.of(Auth.class, auth).orElseThrow(),
                Choice.of(Frontend.class, f).orElseThrow());
    }

    /** The package as a directory, such as {@code com/example/demo}. */
    public String packagePath() {
        return packageName.replace('.', '/');
    }

    /**
     * The options in the shape the guides' checklist filters on (see CONTRIBUTING.md, "Checklist"). Every project has
     * a REST API and no messaging or cache yet.
     */
    public Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("architecture", architecture.id());
        options.put("api", List.of("rest"));
        options.put("database", database.id());
        if (database == Database.POSTGRES) {
            options.put("persistence", "jdbc");
        }
        options.put("kafka.enabled", false);
        options.put("kafka.outbox", false);
        options.put("rabbitmq", false);
        options.put("cache", false);
        options.put("auth", auth == Auth.OIDC);
        options.put("frontend", frontend.id());
        options.put("pyahuCli", false);
        options.put("sample", true);
        return options;
    }

    private static String check(ProjectField field, Map<String, String> fields, List<FieldError> errors) {
        return check(field, fields.get(field.id()), requireNonNull(field.defaultValue(), field.id()), errors);
    }

    private static String check(ProjectField field, @Nullable String value, String fallback, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.strip();
        if (v.length() > field.maxLength()) {
            errors.add(new FieldError(field.id(), "at most " + field.maxLength() + " characters"));
        } else if (!field.choices().isEmpty() && field.choices().stream().noneMatch(c -> c.id().equals(v))) {
            errors.add(new FieldError(
                    field.id(),
                    "must be one of "
                            + String.join(
                                    ", ",
                                    field.choices().stream().map(Choice::id).toList())));
        } else if (!field.pattern().matcher(v).matches()) {
            errors.add(
                    new FieldError(field.id(), "must match " + field.pattern().pattern()));
        }
        return v;
    }

    private static void keywords(ProjectField field, String value, List<FieldError> errors) {
        for (String segment : value.toLowerCase(Locale.ROOT).split("\\.", -1)) {
            if (JAVA_KEYWORDS.contains(segment)) {
                errors.add(new FieldError(field.id(), "'" + segment + "' is a Java keyword"));
            }
        }
    }
}
