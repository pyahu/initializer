package io.pyahu.quality;

import java.util.List;

/** The gates in the order the build runs them, each with the guide step that explains it. */
final class Gates {

    static final String GUIDES = "https://initializer.pyahu.io/guides/";

    record Definition(
            String id,
            String title,
            String purpose,
            List<String> tasks,
            List<String> required,
            String guide,
            String guideUrl,
            String checklist,
            String toolReport,
            String command,
            String fix) {}

    static final List<Definition> ALL = List.of(
            new Definition(
                    "format",
                    "Formatting",
                    "Spotless with palantir-java-format: one style everywhere, no formatting debates in review.",
                    List.of(":spotlessJavaCheck", ":spotlessCheck"),
                    List.of(":spotlessJavaCheck"),
                    "Build quality, step 6",
                    GUIDES + "build-quality/#bld-format",
                    "BLD-08",
                    null,
                    "./gradlew spotlessCheck",
                    "./gradlew spotlessApply formats every file the way the check expects."),
            new Definition(
                    "compile",
                    "Compilation",
                    "javac with Error Prone and NullAway: known bug patterns and possible nulls fail the compile.",
                    List.of(":openApiGenerate", ":compileJava", ":compileTestJava"),
                    List.of(":compileJava", ":compileTestJava"),
                    "Build quality, steps 4 and 5",
                    GUIDES + "build-quality/#bld-error-prone",
                    "BLD-06, BLD-07",
                    null,
                    "./gradlew compileJava compileTestJava",
                    null),
            new Definition(
                    "unit",
                    "Fast tests",
                    "The tests that start no container: business rules, slices of Spring, the contract.",
                    List.of(":test"),
                    List.of(":test"),
                    "Testing, step 2",
                    GUIDES + "testing/#test-domain",
                    "TEST-01",
                    "../tests/test/index.html",
                    "./gradlew test",
                    null),
            new Definition(
                    "architecture",
                    "Architecture rules",
                    "ArchUnit, jMolecules and Spring Modulith: code stays in its layer and its module.",
                    List.of(":test"),
                    List.of(":test"),
                    "Architecture, step 7",
                    GUIDES + "architecture/#arch-rules-tested",
                    "ARCH-11",
                    "../tests/test/index.html",
                    "./gradlew test --tests '*ArchitectureTest' --tests '*ModulithTest'",
                    null),
            new Definition(
                    "integration",
                    "Integration tests",
                    "Tests tagged integration: the whole application, with real dependencies in containers.",
                    List.of(":integrationTest"),
                    List.of(":integrationTest"),
                    "Testing, step 5",
                    GUIDES + "testing/#test-real-database",
                    "TEST-10",
                    "../tests/integrationTest/index.html",
                    "./gradlew integrationTest",
                    null),
            new Definition(
                    "complexity",
                    "Complexity",
                    "PMD: methods too complex to follow, classes that do too much, errors caught and lost.",
                    List.of(":pmdMain"),
                    List.of(":pmdMain"),
                    "Build quality, step 9",
                    GUIDES + "build-quality/#bld-complexity",
                    "BLD-12",
                    "../pmd/main.html",
                    "./gradlew pmdMain",
                    null),
            new Definition(
                    "coverage",
                    "Coverage",
                    "JaCoCo on domain and application: business rules that no test runs.",
                    List.of(":jacocoTestReport", ":jacocoTestCoverageVerification"),
                    List.of(":jacocoTestCoverageVerification"),
                    "Testing, step 9",
                    GUIDES + "testing/#test-strength",
                    "TEST-11",
                    "../jacoco/test/html/index.html",
                    "./gradlew jacocoTestCoverageVerification",
                    null),
            new Definition(
                    "mutation",
                    "Mutation testing",
                    "PIT changes the business rules one small step at a time and checks that a test fails.",
                    List.of(":pitest"),
                    List.of(":pitest"),
                    "Testing, step 9",
                    GUIDES + "testing/#test-strength",
                    "TEST-11",
                    "../pitest/index.html",
                    "./gradlew pitest",
                    null),
            new Definition(
                    "package",
                    "Contract, jar and SBOM",
                    "The OpenAPI contract is valid, the jar builds and the SBOM lists what ships.",
                    List.of(":openApiValidate", ":bootJar", ":cyclonedxBom"),
                    List.of(":openApiValidate", ":bootJar", ":cyclonedxBom"),
                    "Build quality, step 7",
                    GUIDES + "build-quality/#bld-sbom",
                    "BLD-09",
                    null,
                    "./gradlew build",
                    null));

    /** A failed task outside every gate still gets a place in the report, so its failure is never lost. */
    static Definition other(String taskPath) {
        return new Definition(
                "other" + taskPath.replace(':', '-'),
                "Another task",
                "A task outside the quality gates failed: " + taskPath + ".",
                List.of(taskPath),
                List.of(taskPath),
                "CI pipelines, step 9",
                GUIDES + "ci-pipelines/#ci-quality-report",
                "CI-15",
                null,
                "./gradlew " + taskPath,
                null);
    }

    static boolean tracks(String taskPath) {
        for (Definition gate : ALL) {
            if (gate.tasks().contains(taskPath)) {
                return true;
            }
        }
        return false;
    }

    /** Architecture tests run in the test task; they get a gate of their own. */
    static boolean isArchitectureTest(String className) {
        return className.endsWith("ArchitectureTest") || className.endsWith("ModulithTest");
    }

    private Gates() {}
}
