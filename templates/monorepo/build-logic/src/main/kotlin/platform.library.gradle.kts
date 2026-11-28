// Deliberately reusable code, shared by several verticals: no Spring Boot plugin, and no dependency on a vertical
// or its contract (monorepo guide, step 3).
plugins {
    id("platform.java")
    `java-library`
}

configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        check(path == project.path || path.startsWith(":lib-")) {
            "${project.path} must not depend on $path: a library uses other libraries only (monorepo guide, step 3)"
        }
    }
}
