package io.pyahu.start.generation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pyahu.start.generation.domain.Contribution.Property;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationYamlTest {

    @Test
    void nestsKeysInTheOrderTheyFirstAppear() {
        String yaml = ApplicationYaml.write(List.of(
                new Property("spring.application.name", "demo"),
                new Property("management.server.port", "8081"),
                new Property("spring.threads.virtual.enabled", "true"),
                new Property("management.opentelemetry.resource-attributes.[service.version]", "${APP_VERSION:dev}")));
        assertThat(yaml).isEqualTo("""
                spring:
                  application:
                    name: demo
                  threads:
                    virtual:
                      enabled: true

                management:
                  server:
                    port: 8081
                  opentelemetry:
                    resource-attributes:
                      service.version: ${APP_VERSION:dev}
                """);
    }

    @Test
    void rejectsMalformedNames() {
        assertThatThrownBy(() -> ApplicationYaml.segments("a..b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationYaml.segments("a.[b")).isInstanceOf(IllegalArgumentException.class);
    }
}
