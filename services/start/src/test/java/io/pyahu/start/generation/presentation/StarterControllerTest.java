package io.pyahu.start.generation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.pyahu.start.generation.application.DescribeOptions;
import io.pyahu.start.generation.application.GenerateProject;
import io.pyahu.start.generation.domain.Architecture;
import io.pyahu.start.generation.domain.Baseline;
import io.pyahu.start.generation.domain.Pin;
import io.pyahu.start.generation.domain.ProjectArchive;
import io.pyahu.start.generation.domain.ProjectField;
import io.pyahu.start.generation.domain.ProjectRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StarterController.class)
class StarterControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MeterRegistry meters;

    @MockitoBean
    GenerateProject generateProject;

    @MockitoBean
    DescribeOptions describeOptions;

    @TestConfiguration
    static class Meters {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void describesTheOptions() {
        Baseline baseline =
                new Baseline("v0.1-b1", Map.of("java", Pin.of("25.0.4.1+1"), "spring-boot", Pin.of("4.1.1")));
        given(describeOptions.describe())
                .willReturn(new DescribeOptions.Options(baseline, ProjectField.ALL, List.of(Architecture.values())));
        var result = mvc.get().uri("/metadata").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.baseline.springBoot").isEqualTo("4.1.1");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.architectures[0].isDefault")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.fields[0].defaultValue").isEqualTo("demo");
    }

    @Test
    void getGivesTheZipAsAnAttachment() {
        given(generateProject.generate(any())).willReturn(new ProjectArchive("orders.zip", new byte[] {1, 2}));
        var result = mvc.get().uri("/starter.zip?name=orders").exchange();
        assertThat(result).hasStatusOk().hasContentType("application/zip");
        assertThat(result).headers().hasValue("Content-Disposition", "attachment; filename=\"orders.zip\"");
        assertThat(meters.counter("initializer.projects.generated", "architecture", "hexagonal")
                        .count())
                .isPositive();
    }

    @Test
    void postTakesFormFieldsSoCurlDashDWorks() {
        given(generateProject.generate(ProjectRequest.of("orders", null, null, null, "ddd-layered")))
                .willReturn(new ProjectArchive("orders.zip", new byte[] {1}));
        var result = mvc.post()
                .uri("/starter.zip")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("name=orders&architecture=ddd-layered")
                .exchange();
        assertThat(result).hasStatusOk().hasContentType("application/zip");
    }

    @Test
    void anUnknownFieldIsAProblem() {
        var result = mvc.get().uri("/starter.zip?nme=orders").exchange();
        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("nme");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("https://start.pyahu.io/problems/invalid-request");
    }

    @Test
    void aRejectedValueIsAProblemThatNamesTheField() {
        var result =
                mvc.get().uri("/starter.zip?name=Orders&architecture=onion").exchange();
        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").isEqualTo(List.of("name", "architecture"));
    }

    @Test
    void aFieldTooLongIsRejectedBeforeTheEngine() {
        var result = mvc.get().uri("/starter.zip?name=" + "a".repeat(51)).exchange();
        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void aRepeatedFieldIsAProblem() {
        var result = mvc.get().uri("/starter.zip?name=a&name=b").exchange();
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("given more than once");
    }
}
