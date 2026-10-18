// Java 25, Error Prone with NullAway in JSpecify mode and Spotless (build quality guide, steps 4 to 6).
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("net.ltgt.errorprone")
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    "errorprone"(libs.findLibrary("error-prone-core").get())
    "errorprone"(libs.findLibrary("nullaway").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
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
