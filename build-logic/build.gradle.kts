plugins {
    `kotlin-dsl`
}

// A plugin declared in the catalog, as a dependency of the convention plugins that apply it.
fun plugin(p: Provider<PluginDependency>) = p.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(plugin(libs.plugins.spring.boot))
    implementation(plugin(libs.plugins.errorprone))
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.cyclonedx))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
