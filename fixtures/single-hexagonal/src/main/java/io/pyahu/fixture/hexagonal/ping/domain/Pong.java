package io.pyahu.fixture.hexagonal.ping.domain;

/** The answer to a ping. */
public record Pong(String message) {

    public static Pong reply() {
        return new Pong("pong");
    }
}
