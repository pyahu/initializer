package [[packageName]].config;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.HashSet;
import java.util.Set;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.filter.ContextChangeSetFilter;
import liquibase.changelog.filter.DbmsChangeSetFilter;
import liquibase.changelog.filter.IgnoreChangeSetFilter;
import liquibase.changelog.filter.LabelChangeSetFilter;
import liquibase.database.core.PostgresDatabase;
import liquibase.integration.spring.SpringResourceAccessor;
import liquibase.parser.ChangeLogParserFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Readiness stays down while the database lacks a changeset of this release; the migration Job applies them
 * (database guide, steps 5 and 6). Read only: it compares the changelog with Liquibase's table and never takes the
 * lock. Once every changeset is there the answer is kept, so a later database outage does not take every pod out.
 */
@Component("migrations")
@EnableConfigurationProperties(LiquibaseProperties.class)
class PendingMigrations implements HealthIndicator {

    private final JdbcClient jdbc;
    private final Set<Identity> changeSets;
    private final String historyTable;
    private volatile boolean applied;

    PendingMigrations(JdbcClient jdbc, LiquibaseProperties properties, ResourceLoader resourceLoader) throws Exception {
        this.jdbc = jdbc;
        PostgresDatabase database = new PostgresDatabase();
        String schema = properties.getLiquibaseSchema();
        if (schema == null) {
            schema = properties.getDefaultSchema();
        }
        this.historyTable = database.escapeTableName(null, schema, properties.getDatabaseChangeLogTable());
        var configuredContexts = properties.getContexts();
        Contexts contexts = configuredContexts == null ? new Contexts() : new Contexts(configuredContexts);
        var configuredLabels = properties.getLabelFilter();
        LabelExpression labels =
                new LabelExpression(configuredLabels == null ? "" : String.join(",", configuredLabels));
        ChangeLogParameters parameters = new ChangeLogParameters(database);
        parameters.setContexts(contexts);
        parameters.setLabels(labels);
        var configuredParameters = properties.getParameters();
        if (configuredParameters != null) {
            configuredParameters.forEach(parameters::set);
        }
        DatabaseChangeLog changeLog;
        try (var resources = new SpringResourceAccessor(resourceLoader)) {
            String location = properties.getChangeLog();
            changeLog = ChangeLogParserFactory.getInstance()
                    .getParser(location, resources)
                    .parse(location, parameters, resources);
        }
        var contextFilter = new ContextChangeSetFilter(contexts);
        var labelFilter = new LabelChangeSetFilter(labels);
        var dbmsFilter = new DbmsChangeSetFilter(database);
        var ignoreFilter = new IgnoreChangeSetFilter();
        this.changeSets = changeLog.getChangeSets().stream()
                .filter(c -> contextFilter.accepts(c).isAccepted())
                .filter(c -> labelFilter.accepts(c).isAccepted())
                .filter(c -> dbmsFilter.accepts(c).isAccepted())
                .filter(c -> ignoreFilter.accepts(c).isAccepted())
                .map(c -> new Identity(c.getId(), c.getAuthor(), c.getFilePath()))
                .collect(toUnmodifiableSet());
    }

    @Override
    public Health health() {
        if (applied) {
            return Health.up().build();
        }
        Set<Identity> pending = new HashSet<>(changeSets);
        try {
            pending.removeAll(jdbc.sql("SELECT id, author, filename FROM " + historyTable)
                    .query((row, number) ->
                            new Identity(row.getString("id"), row.getString("author"), row.getString("filename")))
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

    private record Identity(String id, String author, String filename) {}
}
