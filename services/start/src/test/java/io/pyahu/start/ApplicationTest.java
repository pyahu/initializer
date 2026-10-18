package io.pyahu.start;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.start.generation.application.DescribeBaseline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTest {

    @Autowired
    DescribeBaseline describeBaseline;

    @Test
    void readsTheActiveBaselineFromTheJar() {
        var baseline = describeBaseline.active();
        assertThat(baseline.id()).matches("v\\d+\\.\\d+-b\\d+");
        assertThat(baseline.javaFeature()).isEqualTo(25);
        assertThat(baseline.springBoot()).startsWith("4.1.");
    }
}
