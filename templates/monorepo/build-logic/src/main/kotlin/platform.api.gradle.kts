// The contract a vertical offers, in src/main/openapi/<vertical>.yaml, and the code generated from it once: HTTP
// service interfaces the vertical's controllers implement and other verticals call, with their models (monorepo
// guide, step 4). An api project depends on libraries only, so it never hands a vertical's code to its consumers.
plugins {
    id("platform.java")
    `java-library`
    id("org.openapi.generator")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

val vertical = project.name.removeSuffix("-api")
val contract = layout.projectDirectory.file("src/main/openapi/$vertical.yaml")
val contractPackage = "${project.group}.api.${vertical.replace("-", "")}"

dependencies {
    "api"(lib("spring-web"))
    "api"(lib("spring-context"))
    "api"(lib("jakarta-validation-api"))
    "api"(lib("jackson-annotations"))
    // The generated code marks itself with @Generated, which only the compiler reads.
    "compileOnly"(lib("jakarta-annotation-api"))
}

configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        check(path == project.path || path.startsWith(":lib-")) {
            "${project.path} must not depend on $path: a contract uses libraries only (monorepo guide, step 3)"
        }
    }
}

openApiValidate {
    inputSpec = contract
}

openApiGenerate {
    generatorName = "spring"
    library = "spring-http-interface"
    inputSpec = contract
    outputDir = layout.buildDirectory.dir("generated/openapi")
    apiPackage = contractPackage
    modelPackage = "$contractPackage.model"
    // The interfaces, the models and their package-info.java; no Spring configuration class.
    globalProperties = mapOf("apis" to "", "models" to "", "supportingFiles" to "package-info.java")
    configOptions = mapOf(
        "useSpringBoot4" to "true",
        "useTags" to "true",
        "useBeanValidation" to "true",
        "openApiNullable" to "false",
        "useJspecify" to "true",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "hideGenerationTimestamp" to "true",
        "sourceFolder" to "",
    )
}

sourceSets.main {
    java.srcDir(tasks.openApiGenerate.map { it.outputDir.get() })
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

tasks.check {
    dependsOn(tasks.openApiValidate)
}
