// orders: hexagonal, with PostgreSQL and OIDC. The conventions are in build-logic; this file adds what this
// vertical uses (monorepo guide, step 5).
plugins {
    id("platform.service")
}

dependencies {
    implementation(project(":orders-api"))
    implementation(libs.jmolecules.hexagonal.architecture)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.liquibase)
    implementation(libs.datasource.micrometer.spring.boot)
    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)

    testImplementation(libs.jmolecules.archunit)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
}

// On the laptop the application migrates on start, as the one database user (database guide, steps 4 and 5), and
// each vertical has its own port, so several run side by side (monorepo guide, step 8).
tasks.bootRun {
    args("--spring.liquibase.enabled=true", "--server.port=8081")
}
