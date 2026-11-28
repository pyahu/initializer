// The [[name]] vertical. The conventions are in build-logic; this file adds what this vertical uses (monorepo
// guide, step 5).
plugins {
    id("platform.service")
}

dependencies {
[[verticalDependencies]]
}

// On the laptop each vertical has its own ports, so several run side by side (monorepo guide, step 8).
[[#postgres]]
// It also migrates on start, as the one database user (database guide, steps 4 and 5).
[[/postgres]]
tasks.bootRun {
    args([[#postgres]]"--spring.liquibase.enabled=true", [[/postgres]]"--server.port=[[port]]", "--management.server.port=[[managementPort]]")
}
