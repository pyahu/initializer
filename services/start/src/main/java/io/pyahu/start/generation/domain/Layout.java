package io.pyahu.start.generation.domain;

/** One service in its repository, or a monorepo of verticals in one build (monorepo guide, ADR 0004). */
public enum Layout implements Choice {
    SINGLE("single", "One service"),
    MONOREPO("monorepo", "Monorepo of verticals");

    private final String id;
    private final String title;

    Layout(String id, String title) {
        this.id = id;
        this.title = title;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return title;
    }
}
