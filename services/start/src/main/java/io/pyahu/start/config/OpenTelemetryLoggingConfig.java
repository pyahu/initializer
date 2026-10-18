package io.pyahu.start.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Boot configures the OpenTelemetry log exporter, but nothing feeds it until the Logback appender is installed. */
@Configuration(proxyBeanMethods = false)
class OpenTelemetryLoggingConfig {

    @Bean
    InitializingBean installOpenTelemetryAppender(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
