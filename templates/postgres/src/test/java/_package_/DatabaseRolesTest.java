package [[packageName]];

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The changelog runs as the migration role; the runtime role writes data and cannot change the schema (step 4). */
@SpringBootTest(properties = "spring.liquibase.enabled=true")
@Import(Postgres.class)
class DatabaseRolesTest {

    @Autowired
    JdbcConnectionDetails database;

    @Test
    void theRuntimeRoleWritesDataButCannotDropATable() throws SQLException {
        try (Connection app = DriverManager.getConnection(database.getJdbcUrl(), "[[dbName]]_app", "local");
                Statement sql = app.createStatement()) {
            assertThat(sql.executeUpdate("UPDATE ping_counter SET answered = answered WHERE id = 1"))
                    .isEqualTo(1);
            assertThatThrownBy(() -> sql.execute("DROP TABLE ping_counter")).hasMessageContaining("must be owner");
        }
    }
}
