package io.pyahu.start.generation.domain;

/** Who may call the service and how users log in (security and Next.js guides). */
public enum Auth implements Choice {
    NONE("none", "None"),
    OIDC("oidc", "OpenID Connect");

    private final String id;
    private final String title;

    Auth(String id, String title) {
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
