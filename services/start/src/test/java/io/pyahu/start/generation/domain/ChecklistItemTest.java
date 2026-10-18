package io.pyahu.start.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChecklistItemTest {

    private final Map<String, Object> options =
            ProjectRequest.of(null, null, null, null, "hexagonal").options();

    @Test
    void anEmptyFilterAlwaysApplies() {
        assertThat(item(Map.of()).appliesTo(options)).isTrue();
    }

    @Test
    void matchesStringsListsAndBooleans() {
        assertThat(item(Map.of("architecture", List.of("hexagonal"))).appliesTo(options))
                .isTrue();
        assertThat(item(Map.of("architecture", List.of("ddd-layered"))).appliesTo(options))
                .isFalse();
        assertThat(item(Map.of("api", List.of("grpc", "rest"))).appliesTo(options))
                .isTrue();
        assertThat(item(Map.of("database", List.of("postgres"))).appliesTo(options))
                .isFalse();
        assertThat(item(Map.of("kafka.enabled", List.of(true))).appliesTo(options))
                .isFalse();
    }

    @Test
    void anOptionTheProjectDoesNotHaveMatchesNothing() {
        assertThat(item(Map.of("persistence", List.of("jdbc"))).appliesTo(options))
                .isFalse();
    }

    private static ChecklistItem item(Map<String, List<Object>> filter) {
        return new ChecklistItem("X-01", 1, "Foundations", "g", "G", "card", 1, "generated-config", "text", filter);
    }
}
