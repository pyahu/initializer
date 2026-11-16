package [[packageName]].config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Compare readiness to real Liquibase runs, including duplicate ids in separate files and filtered changesets. */
class PendingMigrationsTest {

    @Test
    void requiresEachApplicableFileAndRemembersSuccess() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse(
                "postgres@[[postgresDigest]]"))) {
            postgres.start();
            var source =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcClient jdbc = JdbcClient.create(source);
            LiquibaseProperties prod = properties("prod");
            LiquibaseProperties local = properties("dev");
            var readiness = new PendingMigrations(jdbc, prod, new DefaultResourceLoader());
            var localReadiness = new PendingMigrations(jdbc, local, new DefaultResourceLoader());
            assertThat(readiness.health().getStatus()).isEqualTo(Status.DOWN);

            SpringLiquibase migration = new SpringLiquibase();
            migration.setDataSource(source);
            migration.setChangeLog("classpath:/db/readiness/first.yaml");
            migration.afterPropertiesSet();
            // The same id and author in second.yaml must not be mistaken for first.yaml.
            assertThat(readiness.health().getStatus()).isEqualTo(Status.DOWN);

            migration.setChangeLog(prod.getChangeLog());
            migration.setContexts("prod");
            migration.setLabelFilter("stable");
            migration.afterPropertiesSet();
            assertThat(readiness.health().getStatus()).isEqualTo(Status.UP);
            assertThat(localReadiness.health().getStatus()).isEqualTo(Status.DOWN);

            migration.setContexts("dev");
            migration.afterPropertiesSet();
            assertThat(localReadiness.health().getStatus()).isEqualTo(Status.UP);
            assertThat(jdbc.sql("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                            .query(String.class)
                            .list())
                    .contains("readiness_first", "readiness_second", "readiness_dev")
                    .doesNotContain("readiness_beta", "readiness_mysql", "readiness_ignored");

            jdbc.sql("DROP TABLE databasechangelog").update();
            assertThat(readiness.health().getStatus()).isEqualTo(Status.UP);
        }
    }

    private static LiquibaseProperties properties(String context) {
        var properties = new LiquibaseProperties();
        properties.setChangeLog("classpath:/db/readiness/master.yaml");
        properties.setContexts(List.of(context));
        properties.setLabelFilter(List.of("stable"));
        return properties;
    }
}
