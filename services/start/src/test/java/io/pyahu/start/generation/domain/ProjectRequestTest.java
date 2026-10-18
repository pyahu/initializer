package io.pyahu.start.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectRequestTest {

    @Test
    void fillsInTheDefaults() {
        ProjectRequest r = ProjectRequest.of(Map.of());
        assertThat(r.name()).isEqualTo("demo");
        assertThat(r.group()).isEqualTo("com.example");
        assertThat(r.packageName()).isEqualTo("com.example.demo");
        assertThat(r.architecture()).isEqualTo(Architecture.HEXAGONAL);
        assertThat(r.packagePath()).isEqualTo("com/example/demo");
    }

    @Test
    void blankCountsAsAbsentAndThePackageDropsHyphens() {
        ProjectRequest r = ProjectRequest.of(Map.of(
                "name",
                "order-history",
                "group",
                "io.acme",
                "package",
                " ",
                "description",
                "",
                "architecture",
                "ddd-layered"));
        assertThat(r.packageName()).isEqualTo("io.acme.orderhistory");
        assertThat(r.architecture()).isEqualTo(Architecture.DDD_LAYERED);
    }

    @Test
    void reportsEveryRejectedFieldAtOnce() {
        InvalidProjectRequest e = catchThrowableOfType(
                InvalidProjectRequest.class,
                () -> ProjectRequest.of(Map.of(
                        "name",
                        "Orders",
                        "group",
                        "com.Example",
                        "package",
                        "com.example.class",
                        "description",
                        "a \"quote\"",
                        "architecture",
                        "onion")));
        assertThat(e.errors())
                .extracting(FieldError::field)
                .containsExactly("name", "group", "package", "description", "architecture");
    }

    @Test
    void rejectsJavaKeywordsInPackages() {
        InvalidProjectRequest e = catchThrowableOfType(
                InvalidProjectRequest.class, () -> ProjectRequest.of(Map.of("name", "demo", "group", "com.new")));
        assertThat(e.errors()).containsExactly(new FieldError("group", "'new' is a Java keyword"));
    }

    @Test
    void aDerivedPackageDoesNotRepeatTheNameError() {
        InvalidProjectRequest e =
                catchThrowableOfType(InvalidProjectRequest.class, () -> ProjectRequest.of(Map.of("name", "Bad Name")));
        assertThat(e.errors()).extracting(FieldError::field).containsExactly("name");
    }

    @Test
    void rejectsValuesThatAreTooLong() {
        InvalidProjectRequest e = catchThrowableOfType(
                InvalidProjectRequest.class, () -> ProjectRequest.of(Map.of("name", "a".repeat(51))));
        assertThat(e.errors()).containsExactly(new FieldError("name", "at most 50 characters"));
    }
}
