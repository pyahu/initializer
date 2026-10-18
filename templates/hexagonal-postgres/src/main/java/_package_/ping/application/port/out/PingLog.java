package [[packageName]].ping.application.port.out;

/** Counts the pings answered. */
public interface PingLog {

    /** Counts one more ping and returns the total, this one included. */
    long record();
}
