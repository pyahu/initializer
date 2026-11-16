package io.pyahu.start.generation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.start.generation.domain.ProjectRecipe;
import io.pyahu.start.generation.domain.ProjectRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Execute the generated helper with CLI doubles to check rollout barriers, including failure paths. */
class DeployScriptTest {

    @TempDir
    Path directory;

    @Test
    void refusesAnImplicitContext() throws Exception {
        Result result = run(true, false, false, "dev", "release");
        assertThat(result.code()).isEqualTo(2);
        assertThat(result.calls()).isEmpty();
    }

    @Test
    void preparesTheNamespaceWithoutStartingWorkloads() throws Exception {
        Result result = run(true, false, false, "--context", "test-cluster", "dev", "prepare");
        assertThat(result.code()).isZero();
        assertThat(result.calls())
                .contains("deploy-phase=prerequisites")
                .doesNotContain("deploy-phase=migration")
                .doesNotContain("deploy-phase=application")
                .doesNotContain("get secret");
    }

    @Test
    void missingSecretStopsBeforeMigrationAndApplication() throws Exception {
        Result result = run(true, true, false, "--context", "test-cluster", "dev", "release");
        assertThat(result.code()).isNotZero();
        assertThat(result.calls())
                .contains("get secret orders-db-app")
                .doesNotContain("deploy-phase=migration")
                .doesNotContain("deploy-phase=application");
        assertThat(result.output()).contains("Missing or empty key username");
    }

    @Test
    void failedMigrationStopsBeforeApplication() throws Exception {
        Result result = run(true, false, true, "--context", "test-cluster", "dev", "release");
        assertThat(result.code()).isNotZero();
        assertThat(result.calls())
                .contains("wait --for=condition=complete job/orders-migrate-0.1.0")
                .doesNotContain("deploy-phase=application")
                .doesNotContain("rollout status");
    }

    @Test
    void successfulMigrationPrecedesApplicationAndRollout() throws Exception {
        Result result = run(true, false, false, "--context", "test-cluster", "prod", "release");
        assertThat(result.code()).isZero();
        assertThat(result.calls())
                .containsSubsequence(
                        "deploy-phase=prerequisites",
                        "get secret orders-db-app",
                        "get secret orders-db-migrator",
                        "deploy-phase=migration",
                        "wait --for=condition=complete job/orders-migrate-0.1.0",
                        "deploy-phase=application",
                        "rollout status deployment/orders");
        assertThat(result.calls().lines()).allMatch(line -> line.startsWith("--context test-cluster "));
    }

    @Test
    void projectWithoutDatabaseSkipsMigration() throws Exception {
        Result result = run(false, false, false, "--context", "test-cluster", "dev", "release");
        assertThat(result.code()).isZero();
        assertThat(result.calls())
                .contains("deploy-phase=application", "rollout status deployment/orders")
                .doesNotContain("get secret")
                .doesNotContain("deploy-phase=migration");
    }

    private Result run(boolean postgres, boolean missingSecret, boolean migrationFailure, String... arguments)
            throws Exception {
        ClasspathTemplates templates = new ClasspathTemplates();
        var description = new ProjectRecipe(templates, new ClasspathChecklists(templates))
                .describe(
                        ProjectRequest.of(Map.of("name", "orders", "database", postgres ? "postgres" : "none")),
                        new ClasspathBaselines().active());
        var script = new MustacheProjectWriter(templates)
                .files(description).stream()
                        .filter(file -> file.path().equals("deploy/apply.sh"))
                        .findFirst()
                        .orElseThrow();
        Path helper = directory.resolve("apply.sh");
        Files.write(helper, script.content());
        Path bin = Files.createDirectory(directory.resolve("bin"));
        executable(bin.resolve("kustomize"), "#!/usr/bin/env bash\necho rendered\n");
        executable(bin.resolve("kubectl"), """
                #!/usr/bin/env bash
                echo "$*" >> "$TRACE"
                case "$*" in
                  *'get secret'*)
                    test "$MISSING_SECRET" = true || echo present ;;
                  *'deploy-phase=migration'*) echo job/orders-migrate-0.1.0 ;;
                  *'wait --for=condition=complete'*) test "$MIGRATION_FAILURE" != true ;;
                esac
                """);
        Path trace = directory.resolve("trace");
        Path output = directory.resolve("output");
        ProcessBuilder builder = new ProcessBuilder("bash", helper.toString());
        builder.command().addAll(java.util.List.of(arguments));
        builder.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        builder.environment().put("TRACE", trace.toString());
        builder.environment().put("MISSING_SECRET", Boolean.toString(missingSecret));
        builder.environment().put("MIGRATION_FAILURE", Boolean.toString(migrationFailure));
        Process process = builder.redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("deploy helper timed out");
        }
        return new Result(
                process.exitValue(), Files.exists(trace) ? Files.readString(trace) : "", Files.readString(output));
    }

    private static void executable(Path path, String script) throws IOException {
        Files.writeString(path, script);
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }

    record Result(int code, String calls, String output) {}
}
