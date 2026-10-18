package [[packageName]].ping.domain;

[[^postgres]]
/** The answer to a ping. */
public record Pong(String message) {

    public static Pong reply() {
        return new Pong("pong");
    }
}
[[/postgres]]
[[#postgres]]
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
[[/postgres]]
