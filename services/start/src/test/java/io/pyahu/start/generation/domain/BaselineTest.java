package io.pyahu.start.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BaselineTest {

    @Test
    void javaFeatureIsTheFirstNumber() {
        assertThat(baseline("25.0.4.1+1").javaFeature()).isEqualTo(25);
        assertThat(baseline("26+35").javaFeature()).isEqualTo(26);
    }

    @Test
    void aMissingComponentOrFieldFailsLoudly() {
        Baseline b = baseline("25.0.4.1+1");
        assertThatThrownBy(() -> b.version("kafka")).hasMessageContaining("no component kafka");
        assertThatThrownBy(() -> b.required("java", Pin::digest, "digest")).hasMessageContaining("java has no digest");
    }

    private static Baseline baseline(String java) {
        return new Baseline("v0.1-b1", Map.of("java", Pin.of(java), "spring-boot", Pin.of("4.1.1")));
    }
}
