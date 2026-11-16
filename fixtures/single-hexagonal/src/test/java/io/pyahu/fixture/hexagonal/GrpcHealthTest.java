package io.pyahu.fixture.hexagonal;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.grpc.server.autoconfigure.health.GrpcServerHealthAutoConfiguration;
import org.springframework.boot.grpc.server.health.GrpcServerHealth;
import org.springframework.boot.health.application.LivenessStateHealthIndicator;
import org.springframework.boot.health.application.ReadinessStateHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class GrpcHealthTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GrpcServerHealthAutoConfiguration.class))
            .withUserConfiguration(Indicators.class)
            .withPropertyValues("spring.grpc.server.health.enabled=true");

    @Test
    void overallHealthFailsWhenTheDatabaseFails() {
        runner.run(context -> {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
            var states = new HashMap<String, ServingStatus>();
            context.getBean(GrpcServerHealth.class).update(states::put);
            assertThat(states).containsEntry("", ServingStatus.NOT_SERVING);
        });
    }

    @Test
    void namedProbesIgnoreTheDatabaseAndFollowTheirOwnLifecycle() {
        runner.withPropertyValues(
                        "spring.grpc.server.health.service.liveness.include=livenessState",
                        "spring.grpc.server.health.service.readiness.include=readinessState")
                .run(context -> {
                    var health = context.getBean(GrpcServerHealth.class);
                    var states = new HashMap<String, ServingStatus>();
                    AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
                    AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
                    health.update(states::put);
                    assertThat(states)
                            .containsEntry("", ServingStatus.NOT_SERVING)
                            .containsEntry("liveness", ServingStatus.SERVING)
                            .containsEntry("readiness", ServingStatus.SERVING);

                    AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
                    health.update(states::put);
                    assertThat(states)
                            .containsEntry("liveness", ServingStatus.SERVING)
                            .containsEntry("readiness", ServingStatus.NOT_SERVING);

                    AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
                    health.update(states::put);
                    assertThat(states).containsEntry("liveness", ServingStatus.NOT_SERVING);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Indicators {
        @Bean
        ApplicationAvailabilityBean availability() {
            return new ApplicationAvailabilityBean();
        }

        @Bean
        HealthContributorRegistry registry(ApplicationAvailabilityBean availability) {
            Map<String, HealthIndicator> indicators = Map.of(
                    "livenessState", new LivenessStateHealthIndicator(availability),
                    "readinessState", new ReadinessStateHealthIndicator(availability),
                    "db", () -> Health.down().build());
            return new DefaultHealthContributorRegistry(null, register -> indicators.forEach(register));
        }
    }
}
