package io.pyahu.start.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pyahu.start.generation.domain.Contribution.Property;
import io.pyahu.start.generation.domain.Contribution.Value;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectDescriptionTest {

    private final ProjectDescription d =
            new ProjectDescription(ProjectRequest.of(null, null, null, null, null), new Baseline("v0.1-b1", Map.of()));

    @Test
    void twoContributionsWithTheSameKeyConflict() {
        d.add(new Contribution.File("README.md", "base/README.md", false));
        assertThatThrownBy(() -> d.add(new Contribution.File("README.md", "hexagonal/README.md", false)))
                .isInstanceOf(ContributionConflict.class)
                .hasMessageContaining("file README.md is contributed twice");
    }

    @Test
    void aPropertyCannotBecomeASection() {
        d.add(new Property("management.server.port", "8081"));
        assertThatThrownBy(() -> d.add(new Property("management.server", "x")))
                .isInstanceOf(ContributionConflict.class);
        assertThatThrownBy(() -> d.add(new Property("management.server.port.x", "1")))
                .isInstanceOf(ContributionConflict.class);
        d.add(new Property("management.server.address", "0.0.0.0"));
        assertThat(d.all(Property.class)).hasSize(2);
    }

    @Test
    void keepsTheOrderOfContribution() {
        d.add(new Value("b", "2")).add(new Value("a", "1"));
        assertThat(d.all(Value.class)).extracting(Value::name).containsExactly("b", "a");
        assertThat(d.value("a")).isEqualTo("1");
    }
}
