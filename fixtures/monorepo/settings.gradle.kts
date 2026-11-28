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

rootProject.name = "monorepo"

fun foldersWith(dir: String, buildFile: String): List<File> =
    file(dir).listFiles { f -> f.resolve(buildFile).isFile }.orEmpty().sortedBy { it.name }

for (service in foldersWith("services", "build.gradle.kts")) {
    include(":${service.name}")
    project(":${service.name}").projectDir = service
}

// A contract is found on its own, so a vertical's image build sees the contracts it uses without their services.
for (service in foldersWith("services", "api/build.gradle.kts")) {
    include(":${service.name}-api")
    project(":${service.name}-api").projectDir = service.resolve("api")
}

for (library in foldersWith("libs", "build.gradle.kts")) {
    include(":lib-${library.name}")
    project(":lib-${library.name}").projectDir = library
}
