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
    testImplementation(platform(libs.testcontainers.bom))
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
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.grpc.server)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jmolecules.archunit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.liquibase.core)
    testImplementation(libs.postgresql)
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("postgres.image", "postgres@" + libs.versions.postgres.image.get().substringAfter("@"))
}

// Keep database auto-configuration out of the ordinary service tests and its runtime image.
val databaseTest = sourceSets.create("databaseTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[databaseTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[databaseTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    add(databaseTest.implementationConfigurationName, libs.spring.boot.starter.jdbc)
    add(databaseTest.implementationConfigurationName, libs.spring.boot.testcontainers)
}

tasks.named<JavaCompile>(databaseTest.compileJavaTaskName) {
    options.errorprone.enabled = false
}

val databaseTests = tasks.register<Test>("databaseTest") {
    description = "Exercises Spring Boot database integration against real PostgreSQL."
    group = "verification"
    testClassesDirs = databaseTest.output.classesDirs
    classpath = databaseTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(databaseTests)
}

// Exercise the actual generated role grants and changelog, without adding a database to the runtime image.
val databaseRolesResources = tasks.register<Sync>("databaseRolesResources") {
    from("../../templates/postgres/db") {
        include("roles.sql", "init-local.sh")
        into("database-roles/db")
    }
    from("../../templates/postgres/src/main/resources/db/changelog") {
        include("db.changelog-master.yaml")
        into("database-roles/changelog")
    }
    filter { line: String -> line.replace("[[dbName]]", "fixture").replace("[[name]]", "database-roles") }
    into(layout.buildDirectory.dir("generated/database-roles-resources"))
}

sourceSets.test {
    resources.srcDir(databaseRolesResources)
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
