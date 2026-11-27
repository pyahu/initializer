// A vertical: a deployable Spring Boot service with its tests and quality gates (testing guide, step 9; build
// quality guide, step 9). Capabilities such as a database stay in the vertical's own build file.
plugins {
    id("platform.java")
    jacoco
    pmd
    id("org.springframework.boot")
    id("info.solidsoft.pitest")
    id("org.cyclonedx.bom")
}

// One quality report per vertical, its contract included (CI pipelines guide, step 9). It lives in this build, so
// it is applied by its class.
apply<io.pyahu.quality.QualityReportPlugin>()

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()
fun version(alias: String) = libs.findVersion(alias).get().requiredVersion

// Another vertical is reached only through its api project; its code never (monorepo guide, step 3).
configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        check(path == project.path || path.endsWith("-api") || path.startsWith(":lib-")) {
            "${project.path} must not depend on $path: use its api project or a library (monorepo guide, step 3)"
        }
    }
}

dependencies {
    for (bom in listOf("spring-modulith-bom", "jmolecules-bom")) {
        "implementation"(platform(lib(bom)))
        "testImplementation"(platform(lib(bom)))
    }
    "developmentOnly"(platform(lib("spring-boot-dependencies")))

    "implementation"(lib("spring-boot-starter-webmvc"))
    "implementation"(lib("spring-boot-starter-validation"))
    "implementation"(lib("spring-boot-starter-actuator"))
    "implementation"(lib("spring-boot-starter-opentelemetry"))
    "implementation"(lib("opentelemetry-logback-appender"))

    "developmentOnly"(lib("spring-boot-docker-compose"))
    "developmentOnly"(lib("spring-boot-devtools"))

    "testImplementation"(lib("spring-boot-starter-webmvc-test"))
    "testImplementation"(lib("spring-modulith-starter-test"))
    "testImplementation"(lib("archunit-junit5"))
}

// Fast tests on every change; tests tagged "integration" start the application or a container and run after them
// (testing guide, step 9).
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

val sourceSets = the<SourceSetContainer>()

val integrationTest by tasks.registering(Test::class) {
    description = "Runs the tests tagged integration."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

// Complexity and a few defects the compiler checks miss, on handwritten code only (build quality guide, step 9).
pmd {
    toolVersion = version("pmd")
    ruleSets = listOf()
    ruleSetFiles = files(isolated.rootProject.projectDirectory.file("config/pmd/ruleset.xml"))
    isConsoleOutput = true
}

tasks.named<Pmd>("pmdMain") {
    source = fileTree("src/main/java")
}

tasks.named<Pmd>("pmdTest") {
    enabled = false
}

// Coverage from both test tasks; the gate applies to the business rules in domain and application (testing guide,
// step 9).
jacoco {
    toolVersion = version("jacoco")
}

val businessRules = listOf("**/domain/**", "**/application/**")

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    // XML for the quality report, HTML for people.
    reports {
        xml.required = true
    }
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude("**/api/**") } })
}

// The report comes first, so a failed gate still leaves the HTML that shows what is not covered.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test, integrationTest, tasks.named("jacocoTestReport"))
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
val rules = "${project.group}.${project.name.replace("-", "")}"

pitest {
    pitestVersion = version("pitest")
    junit5PluginVersion = version("pitest-junit5")
    targetClasses = setOf("$rules.*.domain.*", "$rules.*.application.*")
    // Only the fast tests of the same packages: no Spring context starts for each mutation.
    targetTests = setOf("$rules.*.domain.*", "$rules.*.application.*")
    excludedGroups = setOf("integration")
    mutationThreshold = 70
    failWhenNoMutations = false
    threads = Runtime.getRuntime().availableProcessors()
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
}

tasks.check {
    dependsOn(
        integrationTest,
        tasks.named("jacocoTestReport"),
        tasks.named("jacocoTestCoverageVerification"),
        tasks.named("pitest"),
    )
}

tasks.cyclonedxBom {
    jsonOutput = layout.buildDirectory.file("sbom/bom.json")
    xmlOutput.unsetConvention()
}

tasks.named("build") {
    dependsOn(tasks.cyclonedxBom)
}
