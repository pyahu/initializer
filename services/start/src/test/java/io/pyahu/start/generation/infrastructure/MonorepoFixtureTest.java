package io.pyahu.start.generation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.start.generation.domain.MonorepoRequest;
import io.pyahu.start.generation.domain.ProjectFile;
import io.pyahu.start.generation.domain.ProjectRecipe;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * fixtures/monorepo is what the engine generates for the options in its fixture.yaml (ADR 0004): every generated
 * file is there byte for byte, and every file there is generated or one of the fixture's own. With
 * {@code -Dfixture.write=true} (./gradlew :start:regenerateFixture) the generated files are written first.
 */
class MonorepoFixtureTest {

    private static final Path ROOT =
            Path.of(System.getProperty("repository.root", "../..")).toAbsolutePath();
    private static final Path FIXTURE = ROOT.resolve("fixtures/monorepo");
    private static final boolean WRITE = Boolean.getBoolean("fixture.write");

    /** What the fixture adds to the generated platform: its manifest, its references and the proof of its rules. */
    private static final Set<String> FIXTURE_ONLY = Set.of(
            "fixture.yaml",
            "docs/references.md",
            "verticals/billing/service/src/test/java/io/pyahu/fixture/billing/VerticalBoundaryTest.java",
            "verticals/billing/service/src/test/java/io/pyahu/fixture/orders/StandIn.java");

    @Test
    void theFixtureIsWhatTheEngineGenerates() throws IOException {
        ClasspathTemplates templates = new ClasspathTemplates();
        ProjectRecipe recipe = new ProjectRecipe(templates, new ClasspathChecklists(templates));
        MustacheProjectWriter writer = new MustacheProjectWriter(templates);
        MonorepoRequest request = MonorepoRequest.of(options());
        List<ProjectFile> files = writer.files(recipe.describe(request, new ClasspathBaselines().active()));

        List<String> problems = new ArrayList<>();
        Set<String> generated = new TreeSet<>();
        for (ProjectFile file : files) {
            generated.add(file.path());
            Path target = FIXTURE.resolve(file.path());
            if (WRITE) {
                Files.createDirectories(target.getParent());
                Files.write(target, file.content());
                target.toFile().setExecutable(file.executable());
            }
            if (!Files.isRegularFile(target)) {
                problems.add(file.path() + ": generated, missing from the fixture");
            } else if (!java.util.Arrays.equals(Files.readAllBytes(target), file.content())) {
                problems.add(file.path() + ": differs from what the engine generates");
            }
        }
        try (Stream<Path> walk = Files.walk(FIXTURE)) {
            for (String path : walk.filter(Files::isRegularFile)
                    .map(p -> FIXTURE.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !ignored(p))
                    .sorted()
                    .toList()) {
                if (!generated.contains(path) && !FIXTURE_ONLY.contains(path)) {
                    problems.add(path + ": in the fixture, not generated; generate it or list it as the fixture's own");
                }
            }
        }
        assertThat(problems)
                .as("drift between fixtures/monorepo and the engine; ./gradlew :start:regenerateFixture rewrites it")
                .isEmpty();
    }

    /** Build output and tool state, never committed. */
    private static boolean ignored(String path) {
        return Stream.of("build/", ".gradle/", ".kotlin/", "out/")
                .anyMatch(dir -> path.startsWith(dir) || path.contains("/" + dir));
    }

    /** The options of fixture.yaml as the fields of a request. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> options() throws IOException {
        Map<String, Object> manifest;
        try (InputStream in = Files.newInputStream(FIXTURE.resolve("fixture.yaml"))) {
            manifest = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        }
        Map<String, Object> o = (Map<String, Object>) manifest.get("options");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("layout", "monorepo");
        fields.put("name", (String) o.get("name"));
        fields.put("group", (String) o.get("group"));
        fields.put("description", (String) o.get("description"));
        fields.put("auth", Boolean.TRUE.equals(o.get("auth")) ? "oidc" : "none");
        fields.put(
                "verticals",
                ((List<Map<String, Object>>) o.get("verticals"))
                        .stream()
                                .map(v -> v.get("name") + ":" + v.get("architecture") + ":" + v.get("database"))
                                .collect(Collectors.joining(",")));
        return fields;
    }
}
