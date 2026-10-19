package io.pyahu.start.generation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.start.generation.domain.Baseline;
import io.pyahu.start.generation.domain.ProjectDescription;
import io.pyahu.start.generation.domain.ProjectFile;
import io.pyahu.start.generation.domain.ProjectRecipe;
import io.pyahu.start.generation.domain.ProjectRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The real templates, checklist and baseline from the jar, rendered without Spring. */
class GenerationTest {

    private static final ClasspathTemplates templates = new ClasspathTemplates();
    private static final Baseline baseline = new ClasspathBaselines().active();
    private static final ProjectRecipe recipe = new ProjectRecipe(templates, new ClasspathChecklists(templates));
    private static final MustacheProjectWriter writer = new MustacheProjectWriter(templates);

    /** Every supported combination, as the matrix workflow builds them. */
    static List<Map<String, String>> combinations() {
        List<Map<String, String>> all = new ArrayList<>();
        for (String architecture : List.of("hexagonal", "ddd-layered")) {
            for (String database : List.of("none", "postgres")) {
                for (String auth : List.of("none", "oidc")) {
                    for (String frontend : List.of("none", "nextjs")) {
                        all.add(Map.of(
                                "name",
                                "orders",
                                "group",
                                "io.acme",
                                "architecture",
                                architecture,
                                "database",
                                database,
                                "auth",
                                auth,
                                "frontend",
                                frontend));
                    }
                }
            }
        }
        return all;
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void everyTemplateRendersWithoutLeftovers(Map<String, String> options) {
        Map<String, String> files = render(ProjectRequest.of(options));
        assertThat(files)
                .containsKeys(
                        ".gitignore",
                        "gradlew",
                        "gradlew.bat",
                        "gradle/wrapper/gradle-wrapper.jar",
                        "build.gradle.kts",
                        "gradle/libs.versions.toml",
                        "Dockerfile",
                        "compose.yml",
                        "renovate.json",
                        ".github/workflows/ci.yml",
                        "deploy/base/deployment.yaml",
                        "deploy/overlays/prod/kustomization.yaml",
                        "src/main/openapi/api.yaml",
                        "src/main/resources/application.yaml",
                        "src/main/java/io/acme/orders/Application.java",
                        "src/test/java/io/acme/orders/ArchitectureTest.java",
                        "CHECKLIST.md");
        files.forEach((path, content) -> {
            if (!path.startsWith("gradlew") && !path.endsWith(".jar")) {
                assertThat(content)
                        .as(path)
                        .doesNotContain("[[")
                        .doesNotContain("{{=")
                        .doesNotContain("\n\n\n");
            }
        });
        boolean postgres = options.get("database").equals("postgres");
        boolean nextjs = options.get("frontend").equals("nextjs");
        assertThat(files.containsKey("db/roles.sql")).isEqualTo(postgres);
        assertThat(files.containsKey("deploy/base/migration-job.yaml")).isEqualTo(postgres);
        assertThat(files.containsKey("frontends/web/package-lock.json")).isEqualTo(nextjs);
        assertThat(files.containsKey("frontends/web/.gitignore")).isEqualTo(nextjs);
        assertThat(files.containsKey("frontends/web/src/auth.ts"))
                .isEqualTo(nextjs && options.get("auth").equals("oidc"));
        assertThat(files.containsKey("src/main/java/io/acme/orders/config/SecurityConfig.java"))
                .isEqualTo(options.get("auth").equals("oidc"));
    }

    @Test
    void theCatalogCarriesTheBaseline() {
        String catalog =
                render(ProjectRequest.of(Map.of("database", "postgres"))).get("gradle/libs.versions.toml");
        assertThat(catalog)
                .contains("spring-boot = \"" + baseline.springBoot() + "\"")
                .contains("openapi-generator = \"" + baseline.version("openapi-generator-gradle-plugin") + "\"")
                .contains("datasource-micrometer = \"" + baseline.version("datasource-micrometer") + "\"")
                .contains("jspecify = { module = \"org.jspecify:jspecify\" }")
                .contains("nullaway = { module = \"com.uber.nullaway:nullaway\", version.ref = \"nullaway\" }");
    }

    @Test
    void onlyHexagonalBringsJMolecules() {
        String hexagonal =
                render(ProjectRequest.of(Map.of("architecture", "hexagonal"))).get("build.gradle.kts");
        String layered =
                render(ProjectRequest.of(Map.of("architecture", "ddd-layered"))).get("build.gradle.kts");
        assertThat(hexagonal).contains("implementation(libs.jmolecules.hexagonal.architecture)");
        assertThat(layered).doesNotContain("jmolecules");
    }

    /** The database image of the tests, Compose and CI is the baseline's, by digest. */
    @Test
    void postgresRunsTheBaselineImage() {
        Map<String, String> files = render(ProjectRequest.of(Map.of("name", "orders", "database", "postgres")));
        String digest = baseline.pin("postgres-image").digest();
        assertThat(files.get("compose.yml")).contains("postgres:" + baseline.version("postgres-image") + "@" + digest);
        assertThat(files.get(".github/workflows/ci.yml")).contains(digest);
        assertThat(files.get("src/test/java/com/example/orders/Postgres.java")).contains("postgres@" + digest);
        assertThat(files.get("src/main/resources/application.yaml"))
                .contains("enabled: false")
                .contains("include: readinessState,migrations");
    }

    /** The workflow template's pins are tooling Renovate moves; kustomize has to stay on the baseline's version. */
    @Test
    void theWorkflowRunsTheBaselineKustomize() {
        String ci = render(ProjectRequest.of(Map.of())).get(".github/workflows/ci.yml");
        Matcher m = Pattern.compile("kustomize/kustomize:v([0-9.]+)@sha256:[0-9a-f]{64}")
                .matcher(ci);
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo(baseline.version("kustomize"));
    }

    /** Renovate moves these pins one file at a time; a pull request that moves only some of them fails here. */
    @Test
    void imagesPinnedInSeveralFilesAgree() {
        Map<String, String> files =
                render(ProjectRequest.of(Map.of("database", "postgres", "auth", "oidc", "frontend", "nextjs")));
        String ci = files.get(".github/workflows/ci.yml");
        String node = files.get("frontends/web/.node-version").strip();
        Matcher nodeImage = Pattern.compile("NODE_IMAGE: (node:[^\\s]+)").matcher(ci);
        assertThat(nodeImage.find()).isTrue();
        assertThat(nodeImage.group(1)).startsWith("node:" + node + "-");
        assertThat(files.get("frontends/web/Dockerfile"))
                .contains("FROM " + nodeImage.group(1) + " AS build")
                .contains("FROM " + nodeImage.group(1) + " AS runtime");
        Matcher oidcImage = Pattern.compile("OIDC_IMAGE: ([^\\s]+)").matcher(ci);
        assertThat(oidcImage.find()).isTrue();
        assertThat(files.get("compose.yml")).contains("image: " + oidcImage.group(1));
    }

    @Test
    void theChecklistKeepsOnlyWhatApplies() {
        String hexagonal =
                render(ProjectRequest.of(Map.of("architecture", "hexagonal"))).get("CHECKLIST.md");
        String layered =
                render(ProjectRequest.of(Map.of("architecture", "ddd-layered"))).get("CHECKLIST.md");
        String everything = render(
                        ProjectRequest.of(Map.of("database", "postgres", "auth", "oidc", "frontend", "nextjs")))
                .get("CHECKLIST.md");
        assertThat(hexagonal)
                .contains("## Part 1: Foundations")
                .contains("- [ ] **BLD-01**")
                .contains("**ARCH-04**")
                .doesNotContain("**KAFKA-")
                .doesNotContain("**DB-")
                .doesNotContain("**SEC-01**")
                .doesNotContain("**NEXT-");
        assertThat(layered).doesNotContain("**ARCH-04**");
        assertThat(everything).contains("**DB-01**").contains("**SEC-01**").contains("**NEXT-04**");
    }

    @Test
    void sameRequestGivesTheSameBytes() {
        ProjectRequest request = ProjectRequest.of(
                Map.of("name", "orders", "architecture", "ddd-layered", "database", "postgres", "frontend", "nextjs"));
        byte[] first = writer.write(recipe.describe(request, baseline)).content();
        byte[] second = writer.write(recipe.describe(request, baseline)).content();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void theZipHasOneFolderAndAnExecutableWrapper() throws IOException {
        byte[] zip = writer.write(recipe.describe(ProjectRequest.of(Map.of("name", "orders")), baseline))
                .content();
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                assertThat(e.getName()).startsWith("orders/");
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        assertThat(entries.get("orders/gradle/wrapper/gradle-wrapper.jar"))
                .isEqualTo(templates.read("base/gradle/wrapper/gradle-wrapper.jar"));
        assertThat(new String(entries.get("orders/gradlew"))).startsWith("#!/bin/sh");
    }

    private static Map<String, String> render(ProjectRequest request) {
        ProjectDescription d = recipe.describe(request, baseline);
        Map<String, String> files = new LinkedHashMap<>();
        for (ProjectFile file : writer.files(d)) {
            files.put(file.path(), file.text().orElse(""));
        }
        return files;
    }
}
