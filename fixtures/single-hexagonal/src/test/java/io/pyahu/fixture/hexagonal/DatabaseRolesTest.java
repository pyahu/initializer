package io.pyahu.fixture.hexagonal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import liquibase.command.CommandScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Reproduces the database guide's role boundary with the generated SQL and changelog on real PostgreSQL. */
@Testcontainers
class DatabaseRolesTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
                    DockerImageName.parse(System.getProperty("postgres.image")))
            .withDatabaseName("fixture")
            .withCopyFileToContainer(MountableFile.forClasspathResource("database-roles/db/roles.sql"), "/db/roles.sql")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("database-roles/db/init-local.sh"),
                    "/docker-entrypoint-initdb.d/init-local.sh");

    @BeforeAll
    static void migrateAsTheSchemaOwner() throws Exception {
        new CommandScope("update")
                .addArgumentValue("url", postgres.getJdbcUrl())
                .addArgumentValue("username", "fixture_migrator")
                .addArgumentValue("password", "local")
                .addArgumentValue("changelogFile", "database-roles/changelog/db.changelog-master.yaml")
                .execute();
    }

    @Test
    void theMigratorOwnsTheSchemaAndItsTables() throws SQLException {
        try (Connection migrator = connection("fixture_migrator");
                Statement sql = migrator.createStatement()) {
            assertThat(migrator.getMetaData().getDatabaseProductVersion()).startsWith("18.7");
            try (var schema =
                    sql.executeQuery("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'fixture'")) {
                assertThat(schema.next()).isTrue();
                assertThat(schema.getString(1)).isEqualTo("fixture_migrator");
            }
            try (var tables = sql.executeQuery(
                    "SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'fixture' ORDER BY tablename")) {
                int count = 0;
                while (tables.next()) {
                    assertThat(tables.getString("tableowner")).isEqualTo("fixture_migrator");
                    count++;
                }
                assertThat(count).isEqualTo(3);
            }
        }
    }

    @Test
    void theRuntimeRoleCanReadInsertUpdateAndDeleteBusinessData() throws SQLException {
        try (Connection app = connection("fixture_app");
                Statement sql = app.createStatement()) {
            assertThat(sql.executeUpdate("INSERT INTO ping_counter (id, answered) VALUES (2, 0)"))
                    .isEqualTo(1);
            assertThat(sql.executeUpdate("UPDATE ping_counter SET answered = 1 WHERE id = 2"))
                    .isEqualTo(1);
            try (var row = sql.executeQuery("SELECT answered FROM ping_counter WHERE id = 2")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong(1)).isEqualTo(1);
            }
            assertThat(sql.executeUpdate("DELETE FROM ping_counter WHERE id = 2"))
                    .isEqualTo(1);
        }
    }

    @Test
    void theRuntimeRoleCannotCreateAlterOrDropTables() throws SQLException {
        try (Connection app = connection("fixture_app");
                Statement sql = app.createStatement()) {
            for (String ddl : List.of(
                    "CREATE TABLE forbidden (id int)",
                    "CREATE TABLE public.forbidden (id int)",
                    "ALTER TABLE ping_counter ADD COLUMN forbidden int",
                    "DROP TABLE ping_counter")) {
                denied(sql, ddl);
            }
        }
    }

    @Test
    void theRuntimeRoleCanReadMigrationHistoryAndLocks() throws SQLException {
        try (Connection app = connection("fixture_app");
                Statement sql = app.createStatement()) {
            for (String table : List.of("databasechangelog", "databasechangeloglock")) {
                try (var rows = sql.executeQuery("SELECT count(*) FROM " + table)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isPositive();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"databasechangelog", "databasechangeloglock"})
    void theRuntimeRoleCannotWriteMigrationBookkeeping(String table) throws SQLException {
        try (Connection app = connection("fixture_app");
                Statement sql = app.createStatement()) {
            denied(sql, "UPDATE " + table + " SET id = id WHERE FALSE");
            denied(sql, "DELETE FROM " + table + " WHERE FALSE");
            denied(sql, "INSERT INTO " + table + " SELECT * FROM " + table + " WHERE FALSE");
        }
    }

    @Test
    void theRuntimeRoleRejectsExcessConnectionsAndRecoversAfterAConnectionCloses() throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement sql = admin.createStatement()) {
            try (var role =
                    sql.executeQuery("SELECT rolconnlimit, rolsuper FROM pg_roles WHERE rolname = 'fixture_app'")) {
                assertThat(role.next()).isTrue();
                assertThat(role.getInt("rolconnlimit")).isEqualTo(40);
                assertThat(role.getBoolean("rolsuper")).isFalse();
            }
            sql.execute("ALTER ROLE fixture_app CONNECTION LIMIT 2");
            try {
                try (Connection first = connection("fixture_app")) {
                    assertThat(first.isValid(1)).isTrue();
                    try (Connection second = connection("fixture_app")) {
                        assertThat(second.isValid(1)).isTrue();
                        assertThatThrownBy(() -> {
                                    try (Connection excess = connection("fixture_app")) {
                                        assertThat(excess.isValid(1)).isTrue();
                                    }
                                })
                                .isInstanceOfSatisfying(SQLException.class, error -> {
                                    assertThat(error.getSQLState()).isEqualTo("53300");
                                    assertThat(error.getMessage())
                                            .contains("too many connections for role", "fixture_app");
                                });
                        // An uncapped ordinary role can still connect: the server itself has capacity.
                        try (Connection migrator = connection("fixture_migrator")) {
                            assertThat(migrator.isValid(1)).isTrue();
                        }
                    }
                    // PostgreSQL may still be processing the closed connection's Terminate message.
                    await().atMost(Duration.ofSeconds(5))
                            .ignoreExceptionsMatching(error ->
                                    error instanceof SQLException sqlError && "53300".equals(sqlError.getSQLState()))
                            .untilAsserted(() -> {
                                try (Connection recovered = connection("fixture_app")) {
                                    assertThat(recovered.isValid(1)).isTrue();
                                }
                            });
                }
            } finally {
                sql.execute("ALTER ROLE fixture_app CONNECTION LIMIT 40");
            }
        }
    }

    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), role, "local");
    }

    private static void denied(Statement sql, String statement) {
        assertThatThrownBy(() -> sql.execute(statement))
                .isInstanceOfSatisfying(
                        SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("42501"));
    }
}
