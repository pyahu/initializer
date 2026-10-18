package io.pyahu.start.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;

class ProjectRequestTest {

    @Test
    void fillsInTheDefaults() {
        ProjectRequest r = ProjectRequest.of(null, null, null, null, null);
        assertThat(r.name()).isEqualTo("demo");
        assertThat(r.group()).isEqualTo("com.example");
        assertThat(r.packageName()).isEqualTo("com.example.demo");
        assertThat(r.architecture()).isEqualTo(Architecture.HEXAGONAL);
        assertThat(r.packagePath()).isEqualTo("com/example/demo");
    }

    @Test
    void blankCountsAsAbsentAndThePackageDropsHyphens() {
        ProjectRequest r = ProjectRequest.of("order-history", "io.acme", " ", "", "ddd-layered");
        assertThat(r.packageName()).isEqualTo("io.acme.orderhistory");
        assertThat(r.architecture()).isEqualTo(Architecture.DDD_LAYERED);
    }

    @Test
    void reportsEveryRejectedFieldAtOnce() {
        InvalidProjectRequest e = catchThrowableOfType(
                InvalidProjectRequest.class,
                () -> ProjectRequest.of("Orders", "com.Example", "com.example.class", "a \"quote\"", "onion"));
        assertThat(e.errors())
                .extracting(FieldError::field)
                .containsExactly("name", "group", "package", "description", "architecture");
    }

    @Test
    void rejectsJavaKeywordsInPackages() {
        InvalidProjectRequest e = catchThrowableOfType(
                InvalidProjectRequest.class, () -> ProjectRequest.of("demo", "com.new", null, null, null));
        assertThat(e.errors()).containsExactly(new FieldError("group", "'new' is a Java keyword"));
    }

    @Test
    void aDerivedPackageDoesNotRepeatTheNameError() {
        InvalidProjectRequest e = catchThrowableOfType(
                InvalidProjectRequest.class, () -> ProjectRequest.of("Bad Name", null, null, null, null));
        assertThat(e.errors()).extracting(FieldError::field).containsExactly("name");
    }

    @Test
    void rejectsValuesThatAreTooLong() {
        InvalidProjectRequest e = catchThrowableOfType(
                InvalidProjectRequest.class, () -> ProjectRequest.of("a".repeat(51), null, null, null, null));
        assertThat(e.errors()).containsExactly(new FieldError("name", "at most 50 characters"));
    }
}
