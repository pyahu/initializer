package [[packageName]].ping.adapter.out.persistence;

import [[packageName]].ping.application.port.out.PingLog;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** One row, one statement: the count changes atomically, whatever the number of pods. */
@Repository
class JdbcPingLog implements PingLog {

    private final JdbcClient jdbc;

    JdbcPingLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long record() {
        return jdbc.sql("UPDATE ping_counter SET answered = answered + 1 WHERE id = 1 RETURNING answered")
                .query(Long.class)
                .single();
    }
}
