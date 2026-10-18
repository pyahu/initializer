import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
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
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        // The OpenAPI models are @NullMarked but leave required fields unset in their no-argument constructor.
        option("NullAway:TreatGeneratedAsUnannotated", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.enabled = false
}

tasks.test {
    useJUnitPlatform()
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
