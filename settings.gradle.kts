// The Initializer's own services. Each service is a flat project with an explicit directory; the
// root project has no sources. Fixtures are separate builds and stay out of this one.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "initializer"

include(":start")
project(":start").projectDir = file("services/start")
