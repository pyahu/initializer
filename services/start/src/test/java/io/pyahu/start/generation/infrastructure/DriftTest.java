package io.pyahu.start.generation.infrastructure;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.start.generation.domain.ProjectFile;
import io.pyahu.start.generation.domain.ProjectRecipe;
import io.pyahu.start.generation.domain.ProjectRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The Initializer app is a project its own engine would produce: services/start/drift.yaml names the options and
 * what each generated file is in this repository. With {@code -Ddrift.write=true} (./gradlew :start:regenerate) the
 * files marked same are written first.
 */
class DriftTest {

    private static final Path ROOT =
            Path.of(System.getProperty("repository.root", "../..")).toAbsolutePath();
    private static final boolean WRITE = Boolean.getBoolean("drift.write");

    record Rule(
            String path,
            String mode,
            @Nullable String to,
            @Nullable String why) {

        boolean matches(String file) {
            return path.endsWith("/**") ? file.startsWith(path.substring(0, path.length() - 2)) : file.equals(path);
        }
    }

    record Drift(Map<String, String> options, Map<String, String> paths, List<Rule> rules) {

        /** Where a generated file lives here: its rule's target, else the longest path prefix that applies. */
        String target(String file, Rule rule) {
            if (rule.to() != null) {
                return rule.to();
            }
            String best = "";
            for (String prefix : paths.keySet()) {
                if (file.startsWith(prefix) && prefix.length() > best.length()) {
                    best = prefix;
                }
            }
            return best.isEmpty() ? file : requireNonNull(paths.get(best)) + file.substring(best.length());
        }
    }

    @Test
    void theAppIsWhatItsOwnEngineGenerates() throws IOException {
        Drift drift = read(ROOT.resolve("services/start/drift.yaml"));
        List<String> problems = new ArrayList<>();
        Set<Rule> used = new LinkedHashSet<>();
        for (ProjectFile file : generate(drift.options())) {
            List<Rule> rules =
                    drift.rules().stream().filter(r -> r.matches(file.path())).toList();
            if (rules.size() != 1) {
                problems.add(file.path() + ": matches " + rules.size() + " rules, needs exactly one");
                continue;
            }
            Rule rule = rules.getFirst();
            used.add(rule);
            Path target = ROOT.resolve(drift.target(file.path(), rule));
            switch (rule.mode()) {
                case "same" -> same(file, target, problems);
                case "extends" -> extendsGenerated(file, target, problems);
                case "adapted" -> {
                    if (rule.why() == null) {
                        problems.add(rule.path() + ": an adapted file says why");
                    }
                    if (!Files.isRegularFile(target)) {
                        problems.add(rule.path() + ": adapted while " + ROOT.relativize(target) + " is missing");
                    }
                }
                case "omitted" -> {
                    if (rule.why() == null) {
                        problems.add(rule.path() + ": an omitted file says why");
                    }
                }
                default -> problems.add(rule.path() + ": unknown mode " + rule.mode());
            }
        }
        for (Rule rule : drift.rules()) {
            if (!used.contains(rule)) {
                problems.add(rule.path() + ": the engine generates no such file; remove the rule");
            }
        }
        assertThat(problems)
                .as("drift between the app and its own engine; ./gradlew :start:regenerate rewrites the same files")
                .isEmpty();
    }

    /** The workflow pins the images again in env; they must be the page's. */
    @Test
    void theWorkflowRunsThePageOnItsOwnPins() throws IOException {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/start.yml"));
        String dockerfile = Files.readString(ROOT.resolve("frontends/start-web/Dockerfile"));
        String packageJson = Files.readString(ROOT.resolve("frontends/start-web/package.json"));
        Matcher node = Pattern.compile("NODE_IMAGE: (\\S+)").matcher(workflow);
        assertThat(node.find()).isTrue();
        assertThat(dockerfile).contains("FROM " + node.group(1) + " AS build");
        Matcher playwright = Pattern.compile("PLAYWRIGHT_IMAGE: mcr.microsoft.com/playwright:v([0-9.]+)-")
                .matcher(workflow);
        assertThat(playwright.find()).isTrue();
        assertThat(packageJson).contains("\"@playwright/test\": \"" + playwright.group(1) + "\"");
    }

    private static void same(ProjectFile file, Path target, List<String> problems) throws IOException {
        if (WRITE) {
            Files.createDirectories(requireNonNull(target.getParent()));
            Files.write(target, file.content());
        }
        if (!Files.isRegularFile(target)) {
            problems.add(ROOT.relativize(target) + ": missing, the engine generates " + file.path());
        } else if (!Arrays.equals(Files.readAllBytes(target), file.content())) {
            problems.add(ROOT.relativize(target) + ": differs from the engine's " + file.path());
        }
    }

    /** Every generated line, in order, with any lines of the app's own between them. */
    private static void extendsGenerated(ProjectFile file, Path target, List<String> problems) throws IOException {
        if (!Files.isRegularFile(target)) {
            problems.add(ROOT.relativize(target) + ": missing, it extends the engine's " + file.path());
            return;
        }
        List<String> mine = Files.readAllLines(target, StandardCharsets.UTF_8);
        int at = 0;
        for (String line : file.text().orElseThrow().lines().toList()) {
            while (at < mine.size() && !mine.get(at).equals(line)) {
                at++;
            }
            if (at == mine.size()) {
                problems.add(ROOT.relativize(target) + ": lacks the engine's line '" + line + "' in that order");
                return;
            }
            at++;
        }
    }

    private static List<ProjectFile> generate(Map<String, String> options) {
        ClasspathTemplates templates = new ClasspathTemplates();
        ProjectRecipe recipe = new ProjectRecipe(templates, new ClasspathChecklists(templates));
        return new MustacheProjectWriter(templates)
                .files(recipe.describe(ProjectRequest.of(options), new ClasspathBaselines().active()));
    }

    @SuppressWarnings("unchecked")
    private static Drift read(Path file) throws IOException {
        Map<String, Object> yaml;
        try (InputStream in = Files.newInputStream(file)) {
            yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        }
        List<Rule> rules = new ArrayList<>();
        for (Map<String, String> r : (List<Map<String, String>>) yaml.get("rules")) {
            rules.add(new Rule(
                    requireNonNull(r.get("path"), "path"),
                    requireNonNull(r.get("mode"), "mode"),
                    r.get("to"),
                    r.get("why")));
        }
        return new Drift(
                new LinkedHashMap<>((Map<String, String>) yaml.get("options")),
                new LinkedHashMap<>((Map<String, String>) yaml.get("paths")),
                rules);
    }
}
