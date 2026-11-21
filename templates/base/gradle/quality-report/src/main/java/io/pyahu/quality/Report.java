package io.pyahu.quality;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Everything the report says, built from the task outcomes and the tools' own reports. */
record Report(
        String project,
        Instant generatedAt,
        String status,
        Gate failedFirst,
        List<Gate> gates,
        Map<String, String> environment,
        String where,
        String runUrl) {

    static final String PASSED = "passed";
    static final String FAILED = "failed";
    static final String NOT_RUN = "not-run";
    static final String INCOMPLETE = "incomplete";

    /** Findings kept per gate; the tool's own report has the rest. */
    static final int MAX_FINDINGS = 50;

    record Finding(String rule, String location, String message, String url) {}

    record Metric(String label, double value, Double minimum, String text) {
        boolean belowMinimum() {
            return minimum != null && value + 1e-9 < minimum;
        }
    }

    record Gate(
            Gates.Definition definition,
            String status,
            boolean fromThisRun,
            long ranAt,
            String headline,
            List<Metric> metrics,
            List<Finding> findings,
            int findingsTotal,
            String failure) {

        String id() {
            return definition.id();
        }
    }

    String headline() {
        return switch (status) {
            case FAILED -> "failed at " + failedFirst.definition().title().toLowerCase(Locale.ROOT);
            case PASSED -> "all " + gates.size() + " gates passed";
            default -> passedCount() + " of " + gates.size() + " gates passed, the others have not run";
        };
    }

    /** The reason a gate failed, in one or a few lines: its findings when there are any, else the task's message. */
    static String reason(Gate g) {
        if (!g.findings().isEmpty()) {
            StringBuilder s = new StringBuilder();
            for (Finding f : g.findings().subList(0, Math.min(3, g.findings().size()))) {
                s.append(f.location()).append('\n');
                f.message().lines().limit(2).forEach(line -> s.append("  ").append(line.strip()).append('\n'));
            }
            if (g.findingsTotal() > 3) {
                s.append("and ").append(g.findingsTotal() - 3).append(" more\n");
            }
            return s.toString().strip();
        }
        return g.failure() == null ? "" : trim(g.failure(), 14, 2400);
    }

    long passedCount() {
        return gates.stream().filter(g -> PASSED.equals(g.status())).count();
    }

    static Report collect(
            String project,
            String gradleVersion,
            String javaToolchain,
            Path projectDir,
            Path buildDir,
            Map<String, TaskRun> runs,
            Map<String, String> thresholds) {
        List<ToolReports.TestCase> fast = ToolReports.tests(buildDir.resolve("test-results/test"), runs.get(":test"));
        List<Gate> gates = new ArrayList<>();
        for (Gates.Definition d : Gates.ALL) {
            gates.add(gate(d, runs, projectDir, buildDir, thresholds, fast));
        }
        runs.values().stream()
                .filter(TaskRun::failed)
                .filter(run -> !Gates.tracks(run.path()))
                .sorted(Comparator.comparingLong(TaskRun::finishedAt))
                .forEach(run -> gates.add(new Gate(
                        Gates.other(run.path()), FAILED, run.current(), run.finishedAt(), "Failed", List.of(), List.of(), 0,
                        run.message().isBlank() ? "The task failed without a message." : run.message())));
        // After a failure in this run, a result from an earlier run says nothing about the code that just failed:
        // the build stopped before those gates.
        boolean stopped = false;
        for (int i = 0; i < gates.size(); i++) {
            Gate g = gates.get(i);
            if (stopped && !g.fromThisRun()) {
                gates.set(i, new Gate(g.definition(), NOT_RUN, false, g.ranAt(), "Not run: the build stopped before it",
                        List.of(), List.of(), 0, null));
            }
            if (FAILED.equals(g.status()) && g.fromThisRun()) {
                stopped = true;
            }
        }
        // The first failure in time, not in the list: with --continue, any gate can fail first.
        Gate failedFirst = gates.stream()
                .filter(g -> FAILED.equals(g.status()))
                .min(Comparator.comparingLong(Gate::ranAt))
                .orElse(null);
        String status = failedFirst != null
                ? FAILED
                : gates.stream().allMatch(g -> PASSED.equals(g.status())) ? PASSED : INCOMPLETE;
        String where = "Local";
        String runUrl = null;
        if ("true".equals(System.getenv("GITHUB_ACTIONS"))) {
            where = "GitHub Actions";
            String server = System.getenv("GITHUB_SERVER_URL");
            String repo = System.getenv("GITHUB_REPOSITORY");
            String id = System.getenv("GITHUB_RUN_ID");
            if (server != null && repo != null && id != null) {
                runUrl = server + "/" + repo + "/actions/runs/" + id;
            }
        } else if (System.getenv("CI") != null) {
            where = "CI";
        }
        return new Report(
                project,
                Instant.now(),
                status,
                failedFirst,
                gates,
                environment(gradleVersion, javaToolchain),
                where,
                runUrl);
    }

    private static Gate gate(
            Gates.Definition d,
            Map<String, TaskRun> runs,
            Path projectDir,
            Path buildDir,
            Map<String, String> thresholds,
            List<ToolReports.TestCase> fast) {
        List<TaskRun> mine = d.tasks().stream().map(runs::get).filter(r -> r != null).toList();
        TaskRun failed = mine.stream()
                .filter(TaskRun::failed)
                .min(Comparator.comparingLong(TaskRun::finishedAt))
                .orElse(null);
        // A gate passes only when every task that decides it passed: the jar alone is not "contract, jar and SBOM".
        List<String> missing = d.required().stream()
                .filter(path -> runs.get(path) == null || !runs.get(path).succeeded())
                .toList();
        boolean ran = failed != null || missing.isEmpty();
        boolean fromThisRun = mine.stream().anyMatch(TaskRun::current);
        long ranAt = failed != null
                ? failed.finishedAt()
                : mine.stream().mapToLong(TaskRun::finishedAt).max().orElse(0);
        String status = failed != null ? FAILED : ran ? PASSED : NOT_RUN;
        String failure = failed == null ? null : failed.message();

        List<Finding> findings = new ArrayList<>();
        List<Metric> metrics = new ArrayList<>();
        String headline;
        switch (d.id()) {
            case "unit", "architecture" -> {
                boolean architecture = d.id().equals("architecture");
                List<ToolReports.TestCase> cases = fast.stream()
                        .filter(t -> Gates.isArchitectureTest(t.className()) == architecture)
                        .toList();
                List<ToolReports.TestCase> broken =
                        cases.stream().filter(t -> t.status().equals("failed")).toList();
                // The test task fails for both gates; each gate fails only for its own tests.
                if (failed != null) {
                    boolean otherGateBroken = fast.stream()
                            .anyMatch(t -> t.status().equals("failed") && Gates.isArchitectureTest(t.className()) != architecture);
                    if (broken.isEmpty() && otherGateBroken) {
                        status = cases.isEmpty() ? NOT_RUN : PASSED;
                        failure = null;
                    }
                }
                broken.forEach(t -> findings.add(testFinding(t)));
                headline = testHeadline(cases, broken, architecture ? "rules" : "tests", ran);
                if (NOT_RUN.equals(status)) {
                    headline = "None ran in this build";
                }
                // A filter such as --tests can leave one gate's tests out of a run that passed.
                if (ran && failed == null && cases.isEmpty()) {
                    status = NOT_RUN;
                    headline = "None ran in this build";
                }
            }
            case "integration" -> {
                List<ToolReports.TestCase> cases =
                        ToolReports.tests(buildDir.resolve("test-results/integrationTest"), runs.get(":integrationTest"));
                List<ToolReports.TestCase> broken =
                        cases.stream().filter(t -> t.status().equals("failed")).toList();
                broken.forEach(t -> findings.add(testFinding(t)));
                headline = testHeadline(cases, broken, "tests", ran);
            }
            case "complexity" -> {
                Path xml = buildDir.resolve("reports/pmd/main.xml");
                TaskRun pmd = runs.get(":pmdMain");
                List<ToolReports.Violation> violations =
                        pmd != null && pmd.wrote(xml) ? ToolReports.pmd(xml, projectDir) : List.of();
                violations.forEach(v -> findings.add(new Finding(v.rule(), v.file() + ":" + v.line(), v.message(), v.url())));
                headline = !ran ? "Not run" : violations.isEmpty() ? "No violations" : plural(violations.size(), "violation");
            }
            case "coverage" -> {
                Path xml = buildDir.resolve("reports/jacoco/test/jacocoTestReport.xml");
                TaskRun report = runs.get(":jacocoTestReport");
                ToolReports.Coverage c = report != null && report.wrote(xml) ? ToolReports.businessCoverage(xml) : null;
                if (c == null || !ran) {
                    headline = "Not run";
                } else {
                    Double lineMin = threshold(thresholds, "line");
                    Double branchMin = threshold(thresholds, "branch");
                    metrics.add(new Metric("Lines", c.lines().ratio(), lineMin, c.lines().covered() + " of " + c.lines().total()));
                    metrics.add(new Metric("Branches", c.branches().ratio(), branchMin, c.branches().total() == 0
                            ? "no branches yet"
                            : c.branches().covered() + " of " + c.branches().total()));
                    c.classes().stream()
                            .filter(k -> k.lines().missed() > 0)
                            .sorted(Comparator.comparingLong((ToolReports.ClassCoverage k) -> k.lines().missed()).reversed())
                            .forEach(k -> findings.add(new Finding(
                                    "LINE",
                                    "src/main/java/" + k.sourceFile(),
                                    k.lines().missed() + " of " + k.lines().total() + " lines not run by any test",
                                    null)));
                    headline = percent(c.lines().ratio()) + " of lines, " + (c.branches().total() == 0
                            ? "no branches yet"
                            : percent(c.branches().ratio()) + " of branches");
                }
            }
            case "mutation" -> {
                Path xml = buildDir.resolve("reports/pitest/mutations.xml");
                TaskRun pitest = runs.get(":pitest");
                List<ToolReports.Mutation> all =
                        pitest != null && pitest.wrote(xml) ? ToolReports.mutations(xml) : List.of();
                long detected = all.stream().filter(ToolReports.Mutation::detected).count();
                if (!ran) {
                    headline = "Not run";
                } else if (all.isEmpty()) {
                    headline = "Nothing to mutate yet";
                } else {
                    double score = (double) detected / all.size();
                    metrics.add(new Metric("Mutants killed", score, threshold(thresholds, "mutation"), detected + " of " + all.size()));
                    headline = detected + " of " + all.size() + " mutants killed (" + percent(score) + ")";
                }
                // One finding per method: forty mutants of one method are one thing to fix, not forty.
                Map<String, List<ToolReports.Mutation>> byMethod = new LinkedHashMap<>();
                all.stream()
                        .filter(m -> !m.detected())
                        .forEach(m -> byMethod.computeIfAbsent(m.mutatedClass() + "#" + m.method(), k -> new ArrayList<>()).add(m));
                for (List<ToolReports.Mutation> group : byMethod.values()) {
                    ToolReports.Mutation m = group.get(0);
                    String pkg = m.mutatedClass().contains(".")
                            ? m.mutatedClass().substring(0, m.mutatedClass().lastIndexOf('.')).replace('.', '/') + "/"
                            : "";
                    boolean uncovered = group.stream().allMatch(x -> "NO_COVERAGE".equals(x.status()));
                    List<Integer> lines = group.stream().map(ToolReports.Mutation::line).distinct().sorted().toList();
                    String kinds = group.stream()
                            .map(x -> x.description().replaceAll(" (in|for) .*$", ""))
                            .map(x -> x.isEmpty() ? x : Character.toLowerCase(x.charAt(0)) + x.substring(1))
                            .distinct()
                            .limit(3)
                            .collect(java.util.stream.Collectors.joining("; "));
                    findings.add(new Finding(
                            m.method() + "()",
                            "src/main/java/" + pkg + m.sourceFile() + ":" + lines.get(0),
                            plural(group.size(), "change") + (uncovered
                                    ? " that no test runs"
                                    : " that no test catches") + ", on line" + (lines.size() == 1 ? " " : "s ")
                                    + lines.stream().limit(8).map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
                                    + (lines.size() > 8 ? " and " + (lines.size() - 8) + " more" : "")
                                    + ". For example: " + kinds + ".",
                            null));
                }
            }
            case "compile" -> {
                if (failed != null) {
                    for (String task : List.of("compileJava", "compileTestJava")) {
                        TaskRun run = runs.get(":" + task);
                        Path log = CompilerOutput.fileFor(buildDir, task);
                        if (run != null && run.failed() && run.wrote(log)) {
                            findings.addAll(compilerErrors(read(log), projectDir));
                        }
                    }
                }
                headline = !ran
                        ? "Not run"
                        : failed == null
                                ? "Compiles with Error Prone and NullAway"
                                : failed.path().equals(":openApiGenerate")
                                        ? "The code could not be generated from the contract"
                                        : "Does not compile";
            }
            case "format" -> {
                if (failure != null) {
                    for (String file : formatViolations(failure, projectDir)) {
                        findings.add(new Finding("palantir-java-format", file, "Not formatted", null));
                    }
                }
                headline = !ran ? "Not run" : failed != null ? plural(Math.max(1, findings.size()), "file") + " not formatted" : "Every file formatted";
            }
            default -> headline = !ran ? "Not run" : failed != null ? "Failed" : "Contract valid, jar and SBOM built";
        }
        if (FAILED.equals(status) && failure != null && failure.isBlank()) {
            failure = "The task failed without a message; run " + d.command() + " to see the output.";
        }
        if (NOT_RUN.equals(status) && failed == null && !missing.isEmpty() && missing.size() < d.required().size()) {
            headline = "Partly run: " + String.join(", ", missing.stream().map(m -> m.substring(1)).toList()) + " did not run";
        }
        int total = findings.size();
        List<Finding> kept = total > MAX_FINDINGS ? List.copyOf(findings.subList(0, MAX_FINDINGS)) : List.copyOf(findings);
        return new Gate(d, status, fromThisRun, ranAt, headline, List.copyOf(metrics), kept, total, failure);
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file) : "";
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static Finding testFinding(ToolReports.TestCase t) {
        String simple = t.className().substring(t.className().lastIndexOf('.') + 1);
        String message = t.message().replaceFirst("^(java\\.lang\\.AssertionError|org\\.opentest4j\\.AssertionFailedError): ", "");
        return new Finding(simple, t.className() + "." + t.name(), trim(message, 12, 1800), null);
    }

    private static String testHeadline(
            List<ToolReports.TestCase> cases, List<ToolReports.TestCase> broken, String noun, boolean ran) {
        if (!ran) {
            return "Not run";
        }
        if (cases.isEmpty()) {
            return "No " + noun;
        }
        double seconds = cases.stream().mapToDouble(ToolReports.TestCase::seconds).sum();
        String time = String.format(Locale.ROOT, "%.1f s", seconds);
        String one = noun.equals("rules") ? "rule" : "test";
        long skipped = cases.stream().filter(t -> t.status().equals("skipped")).count();
        long passed = cases.size() - skipped - broken.size();
        String skippedText = skipped == 0 ? "" : ", " + skipped + " skipped";
        return broken.isEmpty()
                ? plural((int) passed, one) + " passed" + skippedText + " in " + time
                : broken.size() + " of " + plural(cases.size(), one) + " failed" + skippedText;
    }

    private static final Pattern JAVAC = Pattern.compile("^(.+\\.java):(\\d+): (?:error|warning): (?:\\[(\\w+)] )?(.*)$");

    static List<Finding> compilerErrors(String text, Path projectDir) {
        List<Finding> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            Matcher m = JAVAC.matcher(line.strip());
            if (m.matches()) {
                out.add(new Finding(
                        m.group(3) == null ? "javac" : m.group(3),
                        ToolReports.relative(projectDir, m.group(1)) + ":" + m.group(2),
                        m.group(4),
                        m.group(3) == null
                                ? null
                                : m.group(3).equals("NullAway")
                                        ? "https://github.com/uber/NullAway/wiki/Error-Messages"
                                        : "https://errorprone.info/bugpattern/" + m.group(3)));
            }
        }
        return out;
    }

    private static List<String> formatViolations(String text, Path projectDir) {
        List<String> files = new ArrayList<>();
        for (String line : text.split("\n")) {
            String s = line.strip();
            if (s.startsWith("- ") && s.endsWith(".java")) {
                files.add(ToolReports.relative(projectDir, s.substring(2)));
            } else if (s.endsWith(".java") && !s.contains(" ")) {
                files.add(ToolReports.relative(projectDir, s));
            }
        }
        return files;
    }

    private static Map<String, String> environment(String gradleVersion, String javaToolchain) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("Java toolchain", javaToolchain);
        env.put("Gradle JVM", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        env.put("Gradle", gradleVersion);
        env.put("System", System.getProperty("os.name") + " " + System.getProperty("os.version") + ", "
                + System.getProperty("os.arch"));
        env.put("Processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        env.put("Gradle heap", (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
        env.put("Docker", docker() ? "available" : "not found");
        env.put("Time zone", ZoneId.systemDefault().getId());
        env.put("Locale", Locale.getDefault().toLanguageTag());
        env.put("Encoding", Charset.defaultCharset().name());
        String sha = System.getenv("GITHUB_SHA");
        if (sha != null && sha.length() >= 7) {
            env.put("Commit", sha.substring(0, 7));
        }
        return env;
    }

    /** Testcontainers looks in these places too; "not found" explains most integration failures on a laptop. */
    private static boolean docker() {
        if (System.getenv("DOCKER_HOST") != null) {
            return true;
        }
        String home = System.getProperty("user.home", "");
        for (String socket : List.of(
                "/var/run/docker.sock",
                home + "/.docker/run/docker.sock",
                home + "/.orbstack/run/docker.sock",
                home + "/.colima/default/docker.sock",
                home + "/.rd/docker.sock")) {
            if (Files.exists(Path.of(socket))) {
                return true;
            }
        }
        return false;
    }

    private static Double threshold(Map<String, String> thresholds, String key) {
        String value = thresholds.get(key);
        if (value == null) {
            return null;
        }
        double v = Double.parseDouble(value);
        return v > 1 ? v / 100 : v;
    }

    static String percent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    static String plural(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    static String trim(String text, int maxLines, int maxChars) {
        String[] lines = text.strip().split("\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            out.append(lines[i].stripTrailing()).append('\n');
        }
        String s = out.toString().strip();
        if (lines.length > maxLines || s.length() > maxChars) {
            s = (s.length() > maxChars ? s.substring(0, maxChars) : s) + "\n…";
        }
        return s;
    }
}
