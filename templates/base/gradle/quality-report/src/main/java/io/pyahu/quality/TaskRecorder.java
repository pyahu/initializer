package io.pyahu.quality;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskSkippedResult;
import org.gradle.tooling.events.task.TaskSuccessResult;

/**
 * Lives for one Gradle invocation. Each finished task is recorded; on close the records join those of earlier
 * invocations (CI runs the gates as separate steps) and the report is written.
 */
public abstract class TaskRecorder implements BuildService<TaskRecorder.Params>, OperationCompletionListener, AutoCloseable {

    private static final Logger LOG = Logging.getLogger(TaskRecorder.class);

    public interface Params extends BuildServiceParameters {
        /** The project's path, such as {@code :orders}, or {@code :} for a build of one project. */
        Property<String> getProjectPath();

        DirectoryProperty getProjectDirectory();

        DirectoryProperty getBuildDirectory();

        Property<String> getProjectName();

        Property<String> getGradleVersion();

        Property<String> getJavaToolchain();

        MapProperty<String, String> getThresholds();
    }

    private final Map<String, TaskRun> runs = new ConcurrentHashMap<>();

    @Override
    public void onFinish(FinishEvent event) {
        if (!(event instanceof TaskFinishEvent finished)) {
            return;
        }
        String path = local(finished.getDescriptor().getTaskPath());
        if (path == null) {
            return;
        }
        if (!Gates.tracks(path) && !(finished.getResult() instanceof TaskFailureResult)) {
            return;
        }
        long start = finished.getResult().getStartTime();
        long at = finished.getResult().getEndTime();
        TaskRun run;
        if (finished.getResult() instanceof TaskFailureResult failure) {
            run = new TaskRun(path, TaskRun.FAILED, start, at, describe(failure.getFailures()), true);
        } else if (finished.getResult() instanceof TaskSkippedResult) {
            run = new TaskRun(path, TaskRun.SKIPPED, start, at, "", true);
        } else if (finished.getResult() instanceof TaskSuccessResult success && success.isUpToDate()) {
            run = new TaskRun(path, TaskRun.UP_TO_DATE, start, at, "", true);
        } else {
            run = new TaskRun(path, TaskRun.PASSED, start, at, "", true);
        }
        runs.put(path, run);
    }

    /**
     * The task's path as the gates name it ({@code :test}), or null for a task of another project. A service's
     * report also covers the contract tasks of its contract project, {@code <service>-api}; that project's own
     * compile and test tasks would otherwise take the place of the service's.
     */
    private String local(String taskPath) {
        String project = getParameters().getProjectPath().get();
        String task = taskPath.substring(taskPath.lastIndexOf(':'));
        String owner = taskPath.substring(0, taskPath.lastIndexOf(':'));
        if (project.equals(":")) {
            return owner.isEmpty() ? task : null;
        }
        if (owner.equals(project)) {
            return task;
        }
        boolean contract = task.equals(":openApiValidate") || task.equals(":openApiGenerate");
        return owner.equals(project + "-api") && contract ? task : null;
    }

    @Override
    public void close() {
        if (runs.isEmpty()) {
            return;
        }
        Path projectDir = getParameters().getProjectDirectory().get().getAsFile().toPath();
        Path buildDir = getParameters().getBuildDirectory().get().getAsFile().toPath();
        Path out = buildDir.resolve("reports/pyahu");
        try {
            Files.createDirectories(out);
            Map<String, TaskRun> all = merge(out.resolve("tasks.properties"));
            Report report = Report.collect(
                    getParameters().getProjectName().get(),
                    getParameters().getGradleVersion().get(),
                    getParameters().getJavaToolchain().getOrElse("not set"),
                    projectDir,
                    buildDir,
                    all,
                    getParameters().getThresholds().get());
            Files.writeString(out.resolve("quality.json"), JsonReport.render(report), StandardCharsets.UTF_8);
            Files.writeString(out.resolve("quality.html"), HtmlReport.render(report), StandardCharsets.UTF_8);
            Files.writeString(out.resolve("job-summary.md"), SummaryReport.render(report), StandardCharsets.UTF_8);
            LOG.lifecycle("Quality report: {} ({})", out.resolve("quality.html").toUri(), report.headline());
        } catch (IOException | RuntimeException e) {
            // The report must never be the reason a build fails.
            LOG.warn("Quality report not written: {}", e.toString());
        }
    }

    /**
     * CI runs the gates as separate Gradle invocations of one workflow run: their results add up. On a laptop each
     * build stands alone, so a result from yesterday never shows next to today's code.
     */
    // One per build: the Gradle daemon keeps classes, and so static fields, from one build to the next.
    private final String session = System.getenv("GITHUB_RUN_ID") != null
            ? "ci-" + System.getenv("GITHUB_RUN_ID") + "-" + System.getenv().getOrDefault("GITHUB_RUN_ATTEMPT", "1")
            : "local-" + java.util.UUID.randomUUID();

    /** Joins this invocation's runs with the ones recorded before in the same CI run. */
    private Map<String, TaskRun> merge(Path file) throws IOException {
        Properties stored = new Properties();
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                stored.load(in);
            }
        }
        if (!session.equals(stored.getProperty("session"))) {
            stored.clear();
        }
        Map<String, TaskRun> all = new ConcurrentHashMap<>();
        for (String key : stored.stringPropertyNames()) {
            if (key.endsWith(".outcome")) {
                String path = key.substring(0, key.length() - ".outcome".length());
                all.put(path, new TaskRun(
                        path,
                        stored.getProperty(key),
                        Long.parseLong(stored.getProperty(path + ".started", "0")),
                        Long.parseLong(stored.getProperty(path + ".at", "0")),
                        stored.getProperty(path + ".message", ""),
                        false));
            }
        }
        all.putAll(runs);
        Properties next = new Properties();
        next.setProperty("session", session);
        for (TaskRun run : all.values()) {
            next.setProperty(run.path() + ".started", Long.toString(run.startedAt()));
            next.setProperty(run.path() + ".outcome", run.outcome());
            next.setProperty(run.path() + ".at", Long.toString(run.finishedAt()));
            next.setProperty(run.path() + ".message", run.message());
        }
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            next.store(w, "Task outcomes for the Pyahu quality report");
        }
        return all;
    }

    private static String describe(List<? extends Failure> failures) {
        StringBuilder text = new StringBuilder();
        for (Failure failure : failures) {
            append(text, failure, 0);
        }
        return text.toString().strip();
    }

    private static void append(StringBuilder text, Failure failure, int depth) {
        if (depth > 6) {
            return;
        }
        String message = failure.getMessage();
        if (message != null && !message.isBlank() && text.indexOf(message.strip()) < 0) {
            text.append(message.strip()).append('\n');
        }
        for (Failure cause : failure.getCauses()) {
            append(text, cause, depth + 1);
        }
    }
}
