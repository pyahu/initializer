package io.pyahu.start;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.start.generation.application.DescribeOptions;
import io.pyahu.start.generation.application.GenerateProject;
import io.pyahu.start.generation.domain.ProjectRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTest {

    @Autowired
    DescribeOptions describeOptions;

    @Autowired
    GenerateProject generateProject;

    @Test
    void readsTheActiveBaselineFromTheJar() {
        var baseline = describeOptions.describe().baseline();
        assertThat(baseline.id()).matches("v\\d+\\.\\d+-b\\d+");
        assertThat(baseline.javaFeature()).isEqualTo(25);
        assertThat(baseline.springBoot()).startsWith("4.1.");
    }

    @Test
    void generatesAProject() {
        var archive = generateProject.generate(ProjectRequest.of(Map.of("name", "orders")));
        assertThat(archive.fileName()).isEqualTo("orders.zip");
        assertThat(archive.content()).startsWith(0x50, 0x4b, 0x03, 0x04);
    }
}
