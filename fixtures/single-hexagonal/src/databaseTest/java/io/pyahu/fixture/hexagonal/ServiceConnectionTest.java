package io.pyahu.fixture.hexagonal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        classes = ServiceConnectionTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/wrong_database",
            "spring.datasource.username=wrong_user",
            "spring.datasource.password=wrong_password",
            "spring.datasource.hikari.connection-timeout=1000",
            "management.otlp.metrics.export.enabled=false"
        })
@Import(ServiceConnectionTest.Postgres.class)
class ServiceConnectionTest {

    @Autowired
    PostgreSQLContainer postgres;

    @Autowired
    JdbcConnectionDetails details;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void serviceConnectionOverridesStalePropertiesAndQueriesTheContainer() throws Exception {
        assertThat(details.getJdbcUrl()).isEqualTo(postgres.getJdbcUrl());
        assertThat(details.getUsername()).isEqualTo(postgres.getUsername());
        assertThat(dataSource.unwrap(HikariDataSource.class).getJdbcUrl()).isEqualTo(postgres.getJdbcUrl());
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("service_connection_proof");
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(postgres.getUsername());
        assertThat(jdbc.queryForObject("SELECT marker FROM connection_proof", String.class))
                .isEqualTo("from-the-postgres-container");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection(type = JdbcConnectionDetails.class)
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse(System.getProperty("postgres.image")))
                    .withDatabaseName("service_connection_proof")
                    .withInitScript("service-connection.sql");
        }
    }
}
