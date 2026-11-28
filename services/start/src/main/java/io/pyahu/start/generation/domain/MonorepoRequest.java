package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A monorepo of verticals in one Gradle build (monorepo guide, ADR 0004): the platform's name, group and
 * description, one identity provider for all, and each vertical with its own architecture and database.
 */
public record MonorepoRequest(String name, String group, String description, Auth auth, List<Vertical> verticals)
        implements GenerationRequest {

    /** More would make one zip a platform nobody reviews; add the rest as folders later. */
    static final int MAX_VERTICALS = 8;

    /** Vertical names that would clash with the platform's own packages and project names. */
    private static final Set<String> RESERVED = Set.of("api", "lib", "platform", "build-logic");

    /** The first vertical's local port; each next one takes the next ten (8110, 8120, ...). */
    private static final int FIRST_PORT = 8110;

    public MonorepoRequest {
        requireNonNull(name, "name");
        requireNonNull(group, "group");
        requireNonNull(description, "description");
        requireNonNull(auth, "auth");
        verticals = List.copyOf(verticals);
    }

    /**
     * Validates the fields of a monorepo. The architecture, database and frontend belong to each vertical, so the
     * fields of one service are refused.
     *
     * @throws InvalidProjectRequest with every rejected field
     */
    public static MonorepoRequest of(Map<String, String> fields) {
        List<FieldError> errors = new ArrayList<>();
        String n = ProjectRequest.check(ProjectField.NAME, fields, errors);
        String g = ProjectRequest.check(ProjectField.GROUP, fields, errors);
        ProjectRequest.keywords(ProjectField.GROUP, g, errors);
        String d = ProjectRequest.check(ProjectField.DESCRIPTION, fields, errors);
        String auth = ProjectRequest.check(ProjectField.AUTH, fields, errors);
        for (ProjectField single : List.of(
                ProjectField.PACKAGE, ProjectField.ARCHITECTURE, ProjectField.DATABASE, ProjectField.FRONTEND)) {
            String value = fields.get(single.id());
            if (value != null && !value.isBlank()) {
                errors.add(new FieldError(single.id(), "set per vertical in verticals when layout=monorepo"));
            }
        }
        String list = fields.get(ProjectField.VERTICALS.id());
        List<Vertical> verticals = new ArrayList<>();
        if (list == null || list.isBlank()) {
            errors.add(new FieldError(ProjectField.VERTICALS.id(), "list at least one vertical"));
        } else {
            String checked = ProjectRequest.check(ProjectField.VERTICALS, list, "", errors);
            if (errors.stream().noneMatch(e -> e.field().equals(ProjectField.VERTICALS.id()))) {
                verticals = verticals(checked, errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidProjectRequest(errors);
        }
        return new MonorepoRequest(n, g, d, Choice.of(Auth.class, auth).orElseThrow(), verticals);
    }

    private static List<Vertical> verticals(String list, List<FieldError> errors) {
        String field = ProjectField.VERTICALS.id();
        List<Vertical> verticals = new ArrayList<>();
        Set<String> names = new HashSet<>();
        String[] entries = list.split(",", -1);
        if (entries.length > MAX_VERTICALS) {
            errors.add(new FieldError(field, "at most " + MAX_VERTICALS + " verticals; add more later as folders"));
            return verticals;
        }
        for (String entry : entries) {
            String[] parts = entry.split(":", -1);
            String name = parts[0];
            String architecture = parts.length > 1 ? parts[1] : Architecture.DEFAULT.id();
            String database = parts.length > 2 ? parts[2] : Database.NONE.id();
            var a = Choice.of(Architecture.class, architecture);
            var db = Choice.of(Database.class, database);
            if (RESERVED.contains(name)) {
                errors.add(new FieldError(field, name + ": a reserved name"));
            } else if (!names.add(name)) {
                errors.add(new FieldError(field, name + ": listed twice"));
            } else if (a.isEmpty()) {
                errors.add(new FieldError(field, name + ": architecture must be one of hexagonal, ddd-layered"));
            } else if (db.isEmpty()) {
                errors.add(new FieldError(field, name + ": database must be one of none, postgres"));
            } else {
                ProjectRequest.keywords(ProjectField.VERTICALS, name.replace("-", ""), errors);
                verticals.add(
                        new Vertical(name, a.orElseThrow(), db.orElseThrow(), FIRST_PORT + 10 * verticals.size()));
            }
        }
        return verticals;
    }

    /** A vertical as one service of the platform: its package is the group followed by its name. */
    public ProjectRequest service(Vertical vertical) {
        return new ProjectRequest(
                vertical.name(),
                group,
                group + "." + vertical.name().replace("-", ""),
                "The " + vertical.name() + " vertical of " + name,
                vertical.architecture(),
                vertical.database(),
                auth,
                Frontend.NONE);
    }

    /** Whether any vertical keeps a database: the platform's CI then needs PostgreSQL for its smoke tests. */
    public boolean anyPostgres() {
        return verticals.stream().anyMatch(v -> v.database() == Database.POSTGRES);
    }

    /** Each option lists the values of its verticals, so an item applies when it applies to one of them. */
    @Override
    public Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("layout", Layout.MONOREPO.id());
        options.put(
                "architecture",
                verticals.stream().map(v -> v.architecture().id()).distinct().toList());
        options.put("api", List.of("rest"));
        options.put(
                "database",
                verticals.stream().map(v -> v.database().id()).distinct().toList());
        if (anyPostgres()) {
            options.put("persistence", "jdbc");
        }
        options.put("kafka.enabled", false);
        options.put("kafka.outbox", false);
        options.put("rabbitmq", false);
        options.put("cache", false);
        options.put("auth", auth == Auth.OIDC);
        options.put("frontend", Frontend.NONE.id());
        options.put("pyahuCli", false);
        options.put("sample", true);
        return options;
    }
}
