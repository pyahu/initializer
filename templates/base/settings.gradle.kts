pluginManagement {
    // The quality report, built with the project (CI pipelines guide, step 9).
    includeBuild("gradle/quality-report")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "[[name]]"
