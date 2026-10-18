package io.pyahu.start.generation.domain;

/** How the code inside each capability is arranged (architecture guide, step 2). */
public enum Architecture implements Choice {
    HEXAGONAL("hexagonal", "Hexagonal", "https://initializer.pyahu.io/guides/architecture/#arch-hexagonal"),
    DDD_LAYERED("ddd-layered", "DDD layered", "https://initializer.pyahu.io/guides/architecture/#arch-ddd-layered");

    public static final Architecture DEFAULT = HEXAGONAL;

    private final String id;
    private final String title;
    private final String guide;

    Architecture(String id, String title, String guide) {
        this.id = id;
        this.title = title;
        this.guide = guide;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return title;
    }

    public String guide() {
        return guide;
    }
}
