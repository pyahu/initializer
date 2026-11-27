package io.pyahu.fixture.orders;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.fixture.orders.ping.application.port.in.Ping;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The whole application on a real PostgreSQL, migrated by the migration role as the Job does it. */
@SpringBootTest(properties = "spring.liquibase.enabled=true")
@Import(Postgres.class)
@Tag("integration")
class ApplicationTest {

    @Autowired
    Ping ping;

    @Autowired
    @Qualifier("migrations")
    HealthIndicator migrations;

    @Test
    void contextStartsAndAnswers() {
        assertThat(ping.ping().message()).isEqualTo("pong");
    }

    @Test
    void theDatabaseCountsEveryPing() {
        long first = ping.ping().count();
        assertThat(ping.ping().count()).isEqualTo(first + 1);
    }

    @Test
    void readinessSeesEveryChangesetApplied() {
        assertThat(migrations.health().getStatus()).isEqualTo(Status.UP);
    }
}
