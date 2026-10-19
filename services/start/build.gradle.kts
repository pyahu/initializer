plugins {
    id("pyahu.spring-boot-service")
    alias(libs.plugins.openapi.generator)
}

group = "io.pyahu.start"
version = "0.1.0"

dependencies {
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.snakeyaml)
    implementation(libs.jmustache)
}

// The REST contract comes first; the build generates the interfaces and models the web layer implements
// (REST APIs guide, steps 2, 3 and 8).
val contract = layout.projectDirectory.file("src/main/openapi/start.yaml")

openApiValidate {
    inputSpec = contract
}

openApiGenerate {
    generatorName = "spring"
    inputSpec = contract
    outputDir = layout.buildDirectory.dir("generated/openapi")
    apiPackage = "io.pyahu.start.generation.presentation.api"
    modelPackage = "io.pyahu.start.generation.presentation.api.model"
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

// What the engine reads at runtime travels inside the jar: the active baseline (data/versions.yaml is
// the only source), the checklist and the project templates with the Gradle wrapper of this repository.
val engineResources = tasks.register<Sync>("engineResources") {
    into(layout.buildDirectory.dir("generated/engine"))
    from(rootProject.file("data/versions.yaml")) { into("baseline") }
    from(rootProject.file("templates")) { into("templates") }
    from(rootProject.file("gradlew")) { into("templates/base") }
    from(rootProject.file("gradlew.bat")) { into("templates/base") }
    from(rootProject.file("gradle/wrapper/gradle-wrapper.jar")) { into("templates/base/gradle/wrapper") }
    // An index, because a class path cannot list a directory inside a jar reliably.
    val root = destinationDir
    doLast {
        val templates = root.resolve("templates")
        for (wrapper in listOf("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar")) {
            check(templates.resolve("base/$wrapper").isFile) { "the wrapper file $wrapper is missing from the templates" }
        }
        val files = templates.walkTopDown()
            .filter { it.isFile && it.name != "index.txt" }
            .map { it.relativeTo(templates).invariantSeparatorsPath }
            .sorted()
            .toList()
        templates.resolve("index.txt").writeText(files.joinToString("\n", postfix = "\n"))
    }
}

sourceSets.main {
    resources.srcDir(engineResources)
}

// The app against its own engine (DriftTest, drift.yaml): the test reads files across the repository, so they are
// its inputs too.
val repositoryFiles = fileTree(rootDir) {
    include(".gitattributes", ".gitignore", ".dockerignore", "renovate.json", "README.md", "settings.gradle.kts")
    include("gradlew", "gradlew.bat", "gradle/**", ".github/workflows/start.yml")
    include("services/start/**", "frontends/start-web/**")
    exclude("**/build/**", "**/node_modules/**", "**/.next/**", "**/test-results/**")
}

tasks.test {
    systemProperty("repository.root", rootDir.absolutePath)
    inputs.files(repositoryFiles).withPropertyName("repositoryFiles")
}

// Writes the files drift.yaml marks same from the engine's output, after a template changes.
tasks.register<Test>("regenerate") {
    description = "Rewrites the app's files that its own engine generates."
    group = "build"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*.DriftTest") }
    systemProperty("repository.root", rootDir.absolutePath)
    systemProperty("drift.write", "true")
    outputs.upToDateWhen { false }
}
