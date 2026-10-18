// A deployable Spring Boot service: the Boot BOMs, web, actuator and OpenTelemetry, the test stack and an
// SBOM inside the build (build quality guide, step 7; observability guide, step 1).
plugins {
    id("pyahu.java-conventions")
    id("org.springframework.boot")
    id("org.cyclonedx.bom")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    for (bom in listOf("spring-boot-dependencies", "spring-modulith-bom", "jmolecules-bom")) {
        "implementation"(platform(lib(bom)))
        "testImplementation"(platform(lib(bom)))
    }
    "testRuntimeOnly"(platform(lib("spring-boot-dependencies")))

    "implementation"(lib("spring-boot-starter-webmvc"))
    "implementation"(lib("spring-boot-starter-actuator"))
    "implementation"(lib("spring-boot-starter-opentelemetry"))
    "implementation"(lib("opentelemetry-logback-appender"))
    "implementation"(lib("jspecify"))

    "testImplementation"(lib("spring-boot-starter-webmvc-test"))
    "testImplementation"(lib("spring-modulith-starter-test"))
    "testImplementation"(lib("archunit-junit5"))
    "testImplementation"(lib("jmolecules-archunit"))
    "testRuntimeOnly"(lib("junit-platform-launcher"))
}

tasks.cyclonedxBom {
    jsonOutput = layout.buildDirectory.file("sbom/bom.json")
    xmlOutput.unsetConvention()
}

tasks.named("build") {
    dependsOn(tasks.cyclonedxBom)
}
