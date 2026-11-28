// One build for the whole platform (monorepo guide). Every folder in services/ is a vertical, its api/ folder
// the contract it offers and every folder in libs/ a library, each a project with a flat name (step 2). Adding
// a vertical is adding a folder.
pluginManagement {
    includeBuild("build-logic")
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

fun projectsIn(dir: String): List<File> =
    file(dir).listFiles { f -> f.resolve("build.gradle.kts").isFile }.orEmpty().sortedBy { it.name }

for (service in projectsIn("services")) {
    include(":${service.name}")
    project(":${service.name}").projectDir = service
    if (service.resolve("api/build.gradle.kts").isFile) {
        include(":${service.name}-api")
        project(":${service.name}-api").projectDir = service.resolve("api")
    }
}

for (library in projectsIn("libs")) {
    include(":lib-${library.name}")
    project(":lib-${library.name}").projectDir = library
}
