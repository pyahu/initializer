// Every Java project of the platform: Java [[javaFeature]], the versions Spring Boot manages, Error Prone with NullAway
// and Spotless (build quality guide, steps 3 to 6). The other conventions apply this one.
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("net.ltgt.errorprone")
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

group = "[[group]]"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of([[javaFeature]])
    }
}

// One set of versions for every project, libraries and api projects included (monorepo guide, step 6).
dependencies {
    for (configuration in listOf("implementation", "testImplementation", "testRuntimeOnly")) {
        configuration(platform(lib("spring-boot-dependencies")))
    }
    "implementation"(lib("jspecify"))
    "errorprone"(lib("error-prone-core"))
    "errorprone"(lib("nullaway"))
    "testRuntimeOnly"(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        // A package without @NullMarked would be silently unchecked; this makes it a compile error (build quality
        // guide, step 5).
        check("RequireExplicitNullMarking", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        // The OpenAPI models are @NullMarked but leave required fields unset in their no-argument constructor.
        option("NullAway:TreatGeneratedAsUnannotated", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.enabled = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        // Handwritten code only: generated sources are added to the source set too.
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
