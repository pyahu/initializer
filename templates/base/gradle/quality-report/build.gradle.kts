// The quality report: one page and one JSON file over every gate of the build (CI pipelines guide, step 9).
plugins {
    `java-gradle-plugin`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

gradlePlugin {
    plugins {
        create("qualityReport") {
            id = "io.pyahu.quality-report"
            implementationClass = "io.pyahu.quality.QualityReportPlugin"
        }
    }
}
