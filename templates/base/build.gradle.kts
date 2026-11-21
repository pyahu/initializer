import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    pmd
    id("io.pyahu.quality-report")
[[#plugins]]
    alias(libs.plugins.[[accessor]])
[[/plugins]]
}

group = "[[group]]"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of([[javaFeature]])
    }
}

dependencies {
[[#dependencySections]]
[[^first]]

[[/first]]
[[#dependencies]]
    [[configuration]]([[notation]])
[[/dependencies]]
[[/dependencySections]]
}

// The REST contract comes first: the build generates the interfaces the controllers implement.
val contract = layout.projectDirectory.file("src/main/openapi/api.yaml")

openApiValidate {
    inputSpec = contract
}

openApiGenerate {
    generatorName = "spring"
    inputSpec = contract
    outputDir = layout.buildDirectory.dir("generated/openapi")
    apiPackage = "[[apiPackage]]"
    modelPackage = "[[apiPackage]].model"
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot4" to "true",
        "useTags" to "true",
        "skipDefaultInterface" to "true",
        "useSpringBuiltInValidation" to "true",
        "openApiNullable" to "false",
        "useJspecify" to "true",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "hideGenerationTimestamp" to "true",
        "sourceFolder" to "",
    )
}

sourceSets.main {
    java.srcDir(tasks.openApiGenerate.map { it.outputDir.get() })
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

tasks.check {
    dependsOn(tasks.openApiValidate)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        // A package without @NullMarked would be silently unchecked; this makes it a compile error (build quality
        // guide, step 5).
        check("RequireExplicitNullMarking", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        // The OpenAPI models are @NullMarked but leave required fields unset in their no-argument constructor.
        option("NullAway:TreatGeneratedAsUnannotated", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.enabled = false
}

// Fast tests on every change; tests tagged "integration" start the application or a container and run after them
// (testing guide, step 9).
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs the tests tagged integration."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

// Complexity and a few defects the compiler checks miss, on handwritten code only (build quality guide, step 9).
pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSets = listOf()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    isConsoleOutput = true
}

tasks.pmdMain {
    source = fileTree("src/main/java")
}

tasks.pmdTest {
    enabled = false
}

// Coverage from both test tasks; the gate applies to the business rules in domain and application (testing guide,
// step 9).
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val businessRules = listOf("**/domain/**", "**/application/**")

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    // XML for the quality report, HTML for people.
    reports {
        xml.required = true
    }
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude("**/api/**") } })
}

// The report comes first, so a failed gate still leaves the HTML that shows what is not covered.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, integrationTest, tasks.jacocoTestReport)
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { include(businessRules) } })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// Mutation testing: the fast tests must fail when the business rules change (testing guide, step 9).
pitest {
    pitestVersion = libs.versions.pitest.asProvider().get()
    junit5PluginVersion = libs.versions.pitest.junit5.get()
    targetClasses = setOf("[[packageName]].*.domain.*", "[[packageName]].*.application.*")
    // Only the fast tests of the same packages: no Spring context starts for each mutation.
    targetTests = setOf("[[packageName]].*.domain.*", "[[packageName]].*.application.*")
    excludedGroups = setOf("integration")
    mutationThreshold = 70
    failWhenNoMutations = false
    threads = Runtime.getRuntime().availableProcessors()
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification, tasks.pitest)
}
[[#postgres]]

// On the laptop the application migrates on start, as the one database user (database guide, steps 4 and 5).
tasks.bootRun {
    args("--spring.liquibase.enabled=true")
}
[[/postgres]]

spotless {
    java {
        // Handwritten code only: the generated API is in the source set too.
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.cyclonedxBom {
    jsonOutput = layout.buildDirectory.file("sbom/bom.json")
    xmlOutput.unsetConvention()
}

tasks.named("build") {
    dependsOn(tasks.cyclonedxBom)
}
