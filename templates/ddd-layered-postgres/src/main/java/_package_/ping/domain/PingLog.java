package [[packageName]].ping.domain;

/** Counts the pings answered. The infrastructure implements it, so the dependency points at the domain. */
public interface PingLog {

    /** Counts one more ping and returns the total, this one included. */
    long record();
}
