package io.pyahu.start.generation.domain;

import io.pyahu.start.generation.domain.Contribution.CatalogLibrary;
import io.pyahu.start.generation.domain.Contribution.CatalogPlugin;
import io.pyahu.start.generation.domain.Contribution.CatalogVersion;
import io.pyahu.start.generation.domain.Contribution.ChecklistEntry;
import io.pyahu.start.generation.domain.Contribution.Dependency;
import io.pyahu.start.generation.domain.Contribution.Property;
import io.pyahu.start.generation.domain.Contribution.Section;
import io.pyahu.start.generation.domain.Contribution.Value;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What goes into a project: the base every project gets, then the architecture. Each part is a list of
 * contributions; composition happens here, in code, so the templates stay free of logic.
 */
public final class ProjectRecipe {

    /** The template set every project gets. */
    public static final String BASE = "base";

    private final TemplateCatalog templates;
    private final Checklists checklists;

    public ProjectRecipe(TemplateCatalog templates, Checklists checklists) {
        this.templates = templates;
        this.checklists = checklists;
    }

    public ProjectDescription describe(ProjectRequest request, Baseline baseline) {
        ProjectDescription d = new ProjectDescription(request, baseline);
        files(d, BASE);
        files(d, request.architecture().id());
        base(d);
        switch (request.architecture()) {
            case HEXAGONAL -> hexagonal(d);
            case DDD_LAYERED -> dddLayered(d);
        }
        checklist(d);
        return d;
    }

    private void files(ProjectDescription d, String set) {
        String packagePath = d.request().packagePath();
        for (String file : templates.files(set)) {
            String path = file.replace("_package_", packagePath);
            if (path.equals("gitignore") || path.equals("gitattributes")) {
                path = "." + path;
            }
            d.add(new Contribution.File(path, set + "/" + file, path.equals("gradlew")));
        }
    }

    private static void base(ProjectDescription d) {
        ProjectRequest r = d.request();
        Baseline b = d.baseline();

        d.add(new Value("baseline", b.id()))
                .add(new Value("javaFeature", Integer.toString(b.javaFeature())))
                .add(new Value("springBoot", b.springBoot()))
                .add(new Value("gradleVersion", b.version("gradle")))
                .add(new Value("gradleSha256", b.required("gradle", Pin::distributionSha256Sum, "checksum")))
                .add(new Value("jdkImage", image(b, "temurin-jdk-image")))
                .add(new Value("jdkDigest", b.required("temurin-jdk-image", Pin::digest, "digest")))
                .add(new Value("jreImage", image(b, "temurin-jre-image")))
                .add(new Value(
                        "lgtmImage",
                        "grafana/otel-lgtm:" + b.version("otel-lgtm-image") + "@"
                                + b.required("otel-lgtm-image", Pin::digest, "digest")))
                .add(new Value("podSecurityVersion", podSecurityVersion(b)))
                .add(new Value("architectureTitle", r.architecture().title()))
                .add(new Value("architectureGuide", r.architecture().guide()));

        version(d, "spring-boot", "spring-boot");
        version(d, "spring-modulith", "spring-modulith");
        version(d, "opentelemetry-logback-appender", "opentelemetry-logback-appender");
        version(d, "archunit", "archunit");
        version(d, "error-prone", "error-prone");
        version(d, "nullaway", "nullaway");
        version(d, "palantir-java-format", "palantir-java-format");
        version(d, "errorprone-gradle-plugin", "errorprone-gradle-plugin");
        version(d, "spotless", "spotless-gradle-plugin");
        version(d, "cyclonedx", "cyclonedx-gradle-plugin");
        version(d, "openapi-generator", "openapi-generator-gradle-plugin");

        d.add(new CatalogPlugin("spring-boot", "org.springframework.boot", "spring-boot"))
                .add(new CatalogPlugin("errorprone", "net.ltgt.errorprone", "errorprone-gradle-plugin"))
                .add(new CatalogPlugin("spotless", "com.diffplug.spotless", "spotless"))
                .add(new CatalogPlugin("cyclonedx", "org.cyclonedx.bom", "cyclonedx"))
                .add(new CatalogPlugin("openapi-generator", "org.openapi.generator", "openapi-generator"));

        // Three BOMs through platform(), repeated for the configurations that need them (build quality guide,
        // step 3). The jMolecules BOM joins them in the hexagonal recipe.
        library(d, "spring-boot-dependencies", "org.springframework.boot:spring-boot-dependencies", "spring-boot");
        library(d, "spring-modulith-bom", "org.springframework.modulith:spring-modulith-bom", "spring-modulith");
        for (String configuration : List.of("implementation", "testImplementation")) {
            platform(d, configuration, "spring-boot-dependencies");
            platform(d, configuration, "spring-modulith-bom");
        }
        platform(d, "testRuntimeOnly", "spring-boot-dependencies");
        platform(d, "developmentOnly", "spring-boot-dependencies");

        implementation(d, "spring-boot-starter-webmvc", "org.springframework.boot:spring-boot-starter-webmvc");
        implementation(d, "spring-boot-starter-validation", "org.springframework.boot:spring-boot-starter-validation");
        implementation(d, "spring-boot-starter-actuator", "org.springframework.boot:spring-boot-starter-actuator");
        implementation(
                d, "spring-boot-starter-opentelemetry", "org.springframework.boot:spring-boot-starter-opentelemetry");
        library(
                d,
                "opentelemetry-logback-appender",
                "io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0",
                "opentelemetry-logback-appender");
        d.add(new Dependency(Section.MAIN, "implementation", "libs.opentelemetry.logback.appender"));
        implementation(d, "jspecify", "org.jspecify:jspecify");

        // Local loop only: never in the production jar (local development guide, steps 2 and 6).
        library(d, "spring-boot-docker-compose", "org.springframework.boot:spring-boot-docker-compose", null);
        library(d, "spring-boot-devtools", "org.springframework.boot:spring-boot-devtools", null);
        d.add(new Dependency(Section.DEVELOPMENT, "developmentOnly", "libs.spring.boot.docker.compose"))
                .add(new Dependency(Section.DEVELOPMENT, "developmentOnly", "libs.spring.boot.devtools"));

        library(d, "error-prone-core", "com.google.errorprone:error_prone_core", "error-prone");
        library(d, "nullaway", "com.uber.nullaway:nullaway", "nullaway");
        d.add(new Dependency(Section.ANALYSIS, "errorprone", "libs.error.prone.core"))
                .add(new Dependency(Section.ANALYSIS, "errorprone", "libs.nullaway"));

        test(d, "spring-boot-starter-webmvc-test", "org.springframework.boot:spring-boot-starter-webmvc-test", null);
        test(d, "spring-modulith-starter-test", "org.springframework.modulith:spring-modulith-starter-test", null);
        test(d, "archunit-junit5", "com.tngtech.archunit:archunit-junit5", "archunit");

        // One application.yaml with local defaults that each environment overrides (configuration guide, step 1).
        property(d, "spring.application.name", r.name());
        property(d, "spring.threads.virtual.enabled", "true");
        property(d, "spring.lifecycle.timeout-per-shutdown-phase", "20s");
        property(d, "spring.mvc.problemdetails.enabled", "true");
        property(d, "logging.structured.format.console", "ecs");
        property(d, "management.server.port", "8081");
        property(d, "management.endpoints.web.exposure.include", "health,info");
        property(d, "management.endpoint.health.probes.add-additional-paths", "true");
        property(d, "management.opentelemetry.resource-attributes.[service.version]", "${APP_VERSION:dev}");
        property(
                d,
                "management.opentelemetry.resource-attributes.[deployment.environment.name]",
                "${DEPLOYMENT_ENVIRONMENT:local}");
        property(d, "management.otlp.metrics.export.base-time-unit", "seconds");
        property(d, "management.otlp.metrics.export.step", "30s");
        property(d, "management.tracing.sampling.probability", "${TRACING_SAMPLING_PROBABILITY:0.1}");
        property(d, "management.metrics.distribution.percentiles-histogram.[http.server.requests]", "true");
    }

    private static void hexagonal(ProjectDescription d) {
        String p = d.request().packageName();
        version(d, "jmolecules", "jmolecules-bom");
        library(d, "jmolecules-bom", "org.jmolecules:jmolecules-bom", "jmolecules");
        platform(d, "implementation", "jmolecules-bom");
        platform(d, "testImplementation", "jmolecules-bom");
        implementation(d, "jmolecules-hexagonal-architecture", "org.jmolecules:jmolecules-hexagonal-architecture");
        test(d, "jmolecules-archunit", "org.jmolecules.integrations:jmolecules-archunit", null);
        d.add(new Value("apiPackage", p + ".ping.adapter.in.web.api"))
                .add(new Value(
                        "layout",
                        rows(
                                "src/main/java/.../ping/domain/", "The rules, plain Java.",
                                "src/main/java/.../ping/application/port/", "What the capability offers and needs.",
                                "src/main/java/.../ping/application/service/", "The use cases behind the ports.",
                                "src/main/java/.../ping/adapter/", "The web and, later, the database.")));
        runtimeLauncher(d);
    }

    private static void dddLayered(ProjectDescription d) {
        String p = d.request().packageName();
        d.add(new Value("apiPackage", p + ".ping.presentation.api"))
                .add(new Value(
                        "layout",
                        rows(
                                "src/main/java/.../ping/presentation/", "The web layer.",
                                "src/main/java/.../ping/application/", "The use cases.",
                                "src/main/java/.../ping/domain/", "The rules, plain Java.",
                                "src/main/java/.../ping/infrastructure/", "The database and clients, later.")));
        runtimeLauncher(d);
    }

    /** The launcher goes last, so the test section ends the same way in every recipe. */
    private static void runtimeLauncher(ProjectDescription d) {
        library(d, "junit-platform-launcher", "org.junit.platform:junit-platform-launcher", null);
        d.add(new Dependency(Section.TEST, "testRuntimeOnly", "libs.junit.platform.launcher"));
    }

    private void checklist(ProjectDescription d) {
        var options = d.request().options();
        for (ChecklistItem item : checklists.all()) {
            if (item.appliesTo(options)) {
                d.add(new ChecklistEntry(item));
            }
        }
    }

    private static String image(Baseline b, String component) {
        return b.required(component, Pin::image, "image") + "@" + b.required(component, Pin::digest, "digest");
    }

    /** The oldest Kubernetes minor of the baseline, such as {@code v1.36}: what the namespace label pins. */
    private static String podSecurityVersion(Baseline b) {
        return b.components().keySet().stream()
                .filter(k -> k.startsWith("kubernetes-"))
                .map(k -> k.substring("kubernetes-".length()))
                .min(Comparator.comparing((String v) -> Integer.parseInt(v.split("\\.")[0]))
                        .thenComparing(v -> Integer.parseInt(v.split("\\.")[1])))
                .map(v -> "v" + v)
                .orElseThrow(() -> new IllegalStateException("baseline " + b.id() + " has no Kubernetes version"));
    }

    /** Rows of the README's layout table, one per path. */
    private static String rows(String... pathsAndDescriptions) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < pathsAndDescriptions.length; i += 2) {
            rows.add("| `" + pathsAndDescriptions[i] + "` | " + pathsAndDescriptions[i + 1] + " |");
        }
        return String.join("\n", rows);
    }

    private static void version(ProjectDescription d, String alias, String component) {
        d.add(new CatalogVersion(alias, d.baseline().version(component)));
    }

    private static void library(ProjectDescription d, String alias, String module, @Nullable String versionRef) {
        d.add(new CatalogLibrary(alias, module, versionRef));
    }

    private static void platform(ProjectDescription d, String configuration, String alias) {
        d.add(new Dependency(Section.PLATFORMS, configuration, "platform(libs." + alias.replace('-', '.') + ")"));
    }

    private static void implementation(ProjectDescription d, String alias, String module) {
        library(d, alias, module, null);
        d.add(new Dependency(Section.MAIN, "implementation", "libs." + alias.replace('-', '.')));
    }

    private static void test(ProjectDescription d, String alias, String module, @Nullable String versionRef) {
        library(d, alias, module, versionRef);
        d.add(new Dependency(Section.TEST, "testImplementation", "libs." + alias.replace('-', '.')));
    }

    private static void property(ProjectDescription d, String name, String value) {
        d.add(new Property(name, value));
    }
}
