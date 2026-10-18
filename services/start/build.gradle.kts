plugins {
    id("pyahu.spring-boot-service")
}

group = "io.pyahu.start"
version = "0.1.0"

dependencies {
    implementation(libs.snakeyaml)
}

// The active baseline travels inside the jar, so the versions the service generates are the
// versions the guides were proven on (data/versions.yaml is the only source).
val baseline = tasks.register<Copy>("baseline") {
    from(rootProject.file("data/versions.yaml"))
    into(layout.buildDirectory.dir("generated/baseline/baseline"))
}

sourceSets.main {
    resources.srcDir(baseline.map { layout.buildDirectory.dir("generated/baseline").get() })
}
