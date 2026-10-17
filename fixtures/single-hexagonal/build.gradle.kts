import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
    alias(libs.plugins.cyclonedx)
}

group = "io.pyahu.fixture"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.modulith.bom))
    implementation(platform(libs.jmolecules.bom))
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(platform(libs.jmolecules.bom))
    testRuntimeOnly(platform(libs.spring.boot.dependencies))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.opentelemetry.logback.appender)
    implementation(libs.jmolecules.hexagonal.architecture)
    implementation(libs.jspecify)

    errorprone(libs.error.prone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jmolecules.archunit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.enabled = false
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
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
