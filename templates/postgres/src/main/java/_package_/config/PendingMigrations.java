package [[packageName]].config;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.HashSet;
import java.util.Set;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.LiquibaseException;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Readiness stays down while the database lacks a changeset of this release; the migration Job applies them
 * (database guide, steps 5 and 6). Read only: it compares the changelog with Liquibase's table and never takes the
 * lock. Once every changeset is there the answer is kept, so a later database outage does not take every pod out.
 */
@Component("migrations")
class PendingMigrations implements HealthIndicator {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private final JdbcClient jdbc;
    private final Set<String> changeSets;
    private volatile boolean applied;

    PendingMigrations(JdbcClient jdbc) throws LiquibaseException {
        this.jdbc = jdbc;
        ResourceAccessor resources = new ClassLoaderResourceAccessor();
        DatabaseChangeLog changeLog = ChangeLogParserFactory.getInstance()
                .getParser(CHANGELOG, resources)
                .parse(CHANGELOG, new ChangeLogParameters(), resources);
        this.changeSets = changeLog.getChangeSets().stream()
                .map(c -> c.getId() + "::" + c.getAuthor())
                .collect(toUnmodifiableSet());
    }

    @Override
    public Health health() {
        if (applied) {
            return Health.up().build();
        }
        Set<String> pending = new HashSet<>(changeSets);
        try {
            pending.removeAll(jdbc.sql("SELECT id || '::' || author FROM databasechangelog")
                    .query(String.class)
                    .list());
        } catch (DataAccessException e) {
            // No changelog table yet or no database: nothing can be confirmed.
            return Health.down().withDetail("pending", changeSets.size()).build();
        }
        if (!pending.isEmpty()) {
            return Health.down().withDetail("pending", pending.size()).build();
        }
        applied = true;
        return Health.up().build();
    }
}
