// The billing vertical. The conventions are in build-logic; this file adds what this vertical uses (monorepo
// guide, step 5).
plugins {
    id("platform.service")
}

dependencies {
    implementation(project(":billing-api"))
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)

    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
}

// On the laptop each vertical has its own ports, so several run side by side (monorepo guide, step 8).
tasks.bootRun {
    args("--server.port=8110", "--management.server.port=8111")
}
