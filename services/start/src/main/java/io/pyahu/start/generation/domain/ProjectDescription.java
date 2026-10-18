package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything a project consists of, in the order it was contributed. */
public final class ProjectDescription {

    private final ProjectRequest request;
    private final Baseline baseline;
    private final Map<String, Contribution> contributions = new LinkedHashMap<>();
    private final List<String> propertyPaths = new ArrayList<>();

    public ProjectDescription(ProjectRequest request, Baseline baseline) {
        this.request = requireNonNull(request, "request");
        this.baseline = requireNonNull(baseline, "baseline");
    }

    public ProjectRequest request() {
        return request;
    }

    public Baseline baseline() {
        return baseline;
    }

    /**
     * Adds a contribution.
     *
     * @throws ContributionConflict when another contribution has the same key, or a property would turn a value into
     *     a section of application.yaml (as {@code a.b} and {@code a.b.c} would)
     */
    public ProjectDescription add(Contribution contribution) {
        Contribution existing = contributions.get(contribution.key());
        if (existing != null) {
            throw new ContributionConflict(existing, contribution);
        }
        if (contribution instanceof Contribution.Property property) {
            String path = property.name() + ".";
            for (String other : propertyPaths) {
                if (path.startsWith(other) || other.startsWith(path)) {
                    String name = other.substring(0, other.length() - 1);
                    throw new ContributionConflict(
                            requireNonNull(contributions.get("property " + name), name), contribution);
                }
            }
            propertyPaths.add(path);
        }
        contributions.put(contribution.key(), contribution);
        return this;
    }

    public List<Contribution> contributions() {
        return List.copyOf(contributions.values());
    }

    /** The contributions of one type, in order. */
    public <T extends Contribution> List<T> all(Class<T> type) {
        return contributions.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /** A value the recipe set. */
    public String value(String name) {
        Contribution c = contributions.get("value " + name);
        if (!(c instanceof Contribution.Value v)) {
            throw new IllegalStateException("no value " + name);
        }
        return v.value();
    }
}
