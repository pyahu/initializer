package io.pyahu.start.generation.domain;

/** A web frontend next to the service (Next.js guide). */
public enum Frontend implements Choice {
    NONE("none", "None"),
    NEXTJS("nextjs", "Next.js");

    private final String id;
    private final String title;

    Frontend(String id, String title) {
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
