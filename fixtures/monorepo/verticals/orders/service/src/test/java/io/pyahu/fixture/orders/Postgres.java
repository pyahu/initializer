package io.pyahu.fixture.orders;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The production major version, with the roles of db/roles.sql (database guide, steps 4 and 8). With
 * {@code spring.liquibase.enabled=true} the changelog runs as the migration role, the way the Job runs it.
 */
@TestConfiguration(proxyBeanMethods = false)
class Postgres {

    // JDBC only: the application connects as the container's admin, Liquibase as the migration role below.
    @Bean
    @ServiceConnection(type = JdbcConnectionDetails.class)
    PostgreSQLContainer postgres() {
        // postgres:18.7, by digest.
        return new PostgreSQLContainer(DockerImageName.parse(
                        "postgres@sha256:64c8b0d31e4ab6ea665e8426ea406a94bf52de3c05b3a4a8bf713704eefa9328"))
                .withCopyFileToContainer(MountableFile.forHostPath("db/roles.sql"), "/db/roles.sql")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("db/init-local.sh"), "/docker-entrypoint-initdb.d/init-local.sh");
    }

    @Bean
    DynamicPropertyRegistrar migrationRole(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.liquibase.url", postgres::getJdbcUrl);
            registry.add("spring.liquibase.user", () -> "orders_migrator");
            registry.add("spring.liquibase.password", () -> "local");
        };
    }
}
