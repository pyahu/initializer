package io.pyahu.start.generation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.start.generation.domain.Baseline;
import io.pyahu.start.generation.domain.ProjectDescription;
import io.pyahu.start.generation.domain.ProjectRecipe;
import io.pyahu.start.generation.domain.ProjectRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The real templates, checklist and baseline from the jar, rendered without Spring. */
class GenerationTest {

    private static final ClasspathTemplates templates = new ClasspathTemplates();
    private static final Baseline baseline = new ClasspathBaselines().active();
    private static final ProjectRecipe recipe = new ProjectRecipe(templates, new ClasspathChecklists(templates));
    private static final MustacheProjectWriter writer = new MustacheProjectWriter(templates);

    @ParameterizedTest
    @ValueSource(strings = {"hexagonal", "ddd-layered"})
    void everyTemplateRendersWithoutLeftovers(String architecture) {
        Map<String, String> files = render(ProjectRequest.of("orders", "io.acme", null, null, architecture));
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
                assertThat(content).as(path).doesNotContain("[[").doesNotContain("{{=");
            }
        });
    }

    @Test
    void theCatalogCarriesTheBaseline() {
        String catalog = render(ProjectRequest.of(null, null, null, null, null)).get("gradle/libs.versions.toml");
        assertThat(catalog)
                .contains("spring-boot = \"" + baseline.springBoot() + "\"")
                .contains("openapi-generator = \"" + baseline.version("openapi-generator-gradle-plugin") + "\"")
                .contains("jspecify = { module = \"org.jspecify:jspecify\" }")
                .contains("nullaway = { module = \"com.uber.nullaway:nullaway\", version.ref = \"nullaway\" }");
    }

    @Test
    void onlyHexagonalBringsJMolecules() {
        String hexagonal =
                render(ProjectRequest.of(null, null, null, null, "hexagonal")).get("build.gradle.kts");
        String layered =
                render(ProjectRequest.of(null, null, null, null, "ddd-layered")).get("build.gradle.kts");
        assertThat(hexagonal).contains("implementation(libs.jmolecules.hexagonal.architecture)");
        assertThat(layered).doesNotContain("jmolecules");
    }

    /** The workflow template's pins are tooling Renovate moves; kustomize has to stay on the baseline's version. */
    @Test
    void theWorkflowRunsTheBaselineKustomize() {
        String ci = render(ProjectRequest.of(null, null, null, null, null)).get(".github/workflows/ci.yml");
        Matcher m = Pattern.compile("kustomize/kustomize:v([0-9.]+)@sha256:[0-9a-f]{64}")
                .matcher(ci);
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo(baseline.version("kustomize"));
    }

    @Test
    void theChecklistKeepsOnlyWhatApplies() {
        String hexagonal =
                render(ProjectRequest.of(null, null, null, null, "hexagonal")).get("CHECKLIST.md");
        String layered =
                render(ProjectRequest.of(null, null, null, null, "ddd-layered")).get("CHECKLIST.md");
        assertThat(hexagonal)
                .contains("## Part 1: Foundations")
                .contains("- [ ] **BLD-01**")
                .contains("**ARCH-04**")
                .doesNotContain("**KAFKA-")
                .doesNotContain("**DB-");
        assertThat(layered).doesNotContain("**ARCH-04**");
    }

    @Test
    void sameRequestGivesTheSameBytes() {
        ProjectRequest request = ProjectRequest.of("orders", null, null, null, "ddd-layered");
        byte[] first = writer.write(recipe.describe(request, baseline)).content();
        byte[] second = writer.write(recipe.describe(request, baseline)).content();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void theZipHasOneFolderAndAnExecutableWrapper() throws IOException {
        byte[] zip = writer.write(recipe.describe(ProjectRequest.of("orders", null, null, null, null), baseline))
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
        return writer.renderText(d);
    }
}
