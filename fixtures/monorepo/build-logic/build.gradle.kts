// One convention plugin per kind of project (monorepo guide, step 5), and the quality report.
plugins {
    `kotlin-dsl`
}

// A plugin declared in the catalog, as a dependency of the convention plugins that apply it.
fun plugin(p: Provider<PluginDependency>) = p.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(plugin(libs.plugins.spring.boot))
    implementation(plugin(libs.plugins.errorprone))
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.pitest))
    implementation(plugin(libs.plugins.cyclonedx))
    implementation(plugin(libs.plugins.openapi.generator))

    // JUnit at the version Spring Boot manages, like every other test of the platform.
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("qualityReport") {
            id = "io.pyahu.quality-report"
            implementationClass = "io.pyahu.quality.QualityReportPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // The tests start Gradle builds of their own; each needs the catalog and the plugins under test.
    systemProperty("catalog", rootDir.resolve("../gradle/libs.versions.toml").absolutePath)
}
