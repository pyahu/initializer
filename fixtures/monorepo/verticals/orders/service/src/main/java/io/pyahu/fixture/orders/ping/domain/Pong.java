package io.pyahu.fixture.orders.ping.domain;

/**
 * The answer to a ping.
 *
 * @param count pings answered so far, this one included
 */
public record Pong(String message, long count) {

    public static Pong reply(long count) {
        return new Pong("pong", count);
    }
}
