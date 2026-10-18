package io.pyahu.start.generation.domain;

/** The database the service owns (database guide). */
public enum Database implements Choice {
    NONE("none", "None"),
    POSTGRES("postgres", "PostgreSQL");

    private final String id;
    private final String title;

    Database(String id, String title) {
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
