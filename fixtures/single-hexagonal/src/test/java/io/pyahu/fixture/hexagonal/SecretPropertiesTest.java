package io.pyahu.fixture.hexagonal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.constraints.AssertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

@ExtendWith(OutputCaptureExtension.class)
class SecretPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"SYNTHETIC_SINGLE_LINE", "SYNTHETIC_LINE_ONE\nSYNTHETIC_LINE_TWO"})
    void acceptsSecretsWithoutLoggingThem(String secret, CapturedOutput output) {
        try (var context = application(secret).run()) {
            assertThat(context.getBean(SecretProperties.class).apiKey()).isEqualTo(secret);
        }
        assertThat(output.getAll()).doesNotContain("SYNTHETIC_");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "${missing-key}", "SYNTHETIC_PREFIX${missing-key}SYNTHETIC_SUFFIX"})
    void failsStartupWithoutPrintingRejectedSecrets(String secret, CapturedOutput output) {
        assertThatThrownBy(() -> application(secret).run()).isInstanceOf(ConfigurationPropertiesBindException.class);
        // The fixture uses structured JSON logging; also accept Boot's plain console format.
        assertThat(output.getAll().replace("\\\"", "\""))
                .contains("Value: \"false\"", "Reason: secret is missing or unresolved")
                .doesNotContain("SYNTHETIC_PREFIX", "SYNTHETIC_SUFFIX", "${missing-key}");
    }

    @Test
    void recordStringDoesNotRevealTheSecret() {
        assertThat(new SecretProperties("SYNTHETIC_SECRET").toString()).isEqualTo("SecretProperties[apiKey=***]");
    }

    private static SpringApplication application(String secret) {
        var application = new SpringApplication(PropertiesConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(secret == null ? Map.of() : Map.of("payments.api-key", secret));
        return application;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecretProperties.class)
    @Import(ValidationAutoConfiguration.class)
    static class PropertiesConfiguration {}

    @ConfigurationProperties("payments")
    @Validated
    public record SecretProperties(String apiKey) {

        @AssertTrue(message = "secret is missing or unresolved")
        public boolean isApiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank() && !apiKey.contains("${");
        }

        @Override
        public String toString() {
            return "SecretProperties[apiKey=***]";
        }
    }
}
