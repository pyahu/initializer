package [[packageName]];

import static org.assertj.core.api.Assertions.assertThat;

import [[packageName]].ping.application.port.in.Ping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
[[#postgres]]
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
[[/postgres]]
import org.springframework.boot.test.context.SpringBootTest;
[[#postgres]]
import org.springframework.context.annotation.Import;
[[/postgres]]

[[#postgres]]
/** The whole application on a real PostgreSQL, migrated by the migration role as the Job does it. */
[[/postgres]]
[[^postgres]]
@SpringBootTest
[[/postgres]]
[[#postgres]]
@SpringBootTest(properties = "spring.liquibase.enabled=true")
@Import(Postgres.class)
[[/postgres]]
class ApplicationTest {

    @Autowired
    Ping ping;
[[#postgres]]

    @Autowired
    @Qualifier("migrations")
    HealthIndicator migrations;
[[/postgres]]

    @Test
    void contextStartsAndAnswers() {
        assertThat(ping.ping().message()).isEqualTo("pong");
    }
[[#postgres]]

    @Test
    void theDatabaseCountsEveryPing() {
        long first = ping.ping().count();
        assertThat(ping.ping().count()).isEqualTo(first + 1);
    }

    @Test
    void readinessSeesEveryChangesetApplied() {
        assertThat(migrations.health().getStatus()).isEqualTo(Status.UP);
    }
[[/postgres]]
}
