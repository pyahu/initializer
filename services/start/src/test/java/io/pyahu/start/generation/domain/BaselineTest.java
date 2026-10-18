package io.pyahu.start.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BaselineTest {

    @Test
    void javaFeatureIsTheFirstNumber() {
        assertThat(new Baseline("v0.1-b1", "25.0.4.1+1", "4.1.1").javaFeature()).isEqualTo(25);
        assertThat(new Baseline("v0.1-b1", "26+35", "4.2.0").javaFeature()).isEqualTo(26);
    }
}
