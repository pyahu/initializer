package io.pyahu.start.generation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.pyahu.start.generation.application.DescribeOptions;
import io.pyahu.start.generation.application.GenerateProject;
import io.pyahu.start.generation.application.PreviewProject;
import io.pyahu.start.generation.domain.Baseline;
import io.pyahu.start.generation.domain.Pin;
import io.pyahu.start.generation.domain.ProjectArchive;
import io.pyahu.start.generation.domain.ProjectField;
import io.pyahu.start.generation.domain.ProjectFile;
import io.pyahu.start.generation.domain.ProjectRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    PreviewProject previewProject;

    @MockitoBean
    DescribeOptions describeOptions;

    @TestConfiguration
    static class Meters {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        GenerationLimit generationLimit() {
            return new GenerationLimit(1);
        }
    }

    @Test
    void describesTheOptions() {
        Baseline baseline =
                new Baseline("v0.1-b1", Map.of("java", Pin.of("25.0.4.1+1"), "spring-boot", Pin.of("4.1.1")));
        given(describeOptions.describe()).willReturn(new DescribeOptions.Options(baseline, ProjectField.ALL));
        var result = mvc.get().uri("/metadata").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.baseline.springBoot").isEqualTo("4.1.1");
        assertThat(result).bodyJson().extractingPath("$.fields[0].defaultValue").isEqualTo("demo");
        assertThat(result).bodyJson().extractingPath("$.fields[0].choices").isEqualTo(List.of());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fields[?(@.id == 'database')].choices[*].id")
                .isEqualTo(List.of("none", "postgres"));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fields[?(@.id == 'architecture')].choices[0].isDefault")
                .isEqualTo(List.of(true));
    }

    @Test
    void getGivesTheZipAsAnAttachment() {
        given(generateProject.generate(any())).willReturn(new ProjectArchive("orders.zip", new byte[] {1, 2}));
        var result = mvc.get().uri("/starter.zip?name=orders").exchange();
        assertThat(result).hasStatusOk().hasContentType("application/zip");
        assertThat(result).headers().hasValue("Content-Disposition", "attachment; filename=\"orders.zip\"");
        assertThat(meters.get("initializer.projects.generated")
                        .tags("architecture", "hexagonal", "database", "none", "auth", "none", "frontend", "none")
                        .counter()
                        .count())
                .isPositive();
    }

    @Test
    void postTakesFormFieldsSoCurlDashDWorks() {
        given(generateProject.generate(ProjectRequest.of(Map.of("name", "orders", "architecture", "ddd-layered"))))
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
    void everyOptionIsAField() {
        given(generateProject.generate(ProjectRequest.of(
                        Map.of("name", "orders", "database", "postgres", "auth", "oidc", "frontend", "nextjs"))))
                .willReturn(new ProjectArchive("orders.zip", new byte[] {1}));
        var result = mvc.get()
                .uri("/starter.zip?name=orders&database=postgres&auth=oidc&frontend=nextjs")
                .exchange();
        assertThat(result).hasStatusOk();
    }

    @Test
    void anUnknownChoiceNamesTheChoices() {
        var result = mvc.get().uri("/starter.zip?database=mysql").exchange();
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must be one of none, postgres");
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

    @Test
    void previewGivesEachFileWithItsText() {
        given(previewProject.preview(ProjectRequest.of(Map.of("name", "orders"))))
                .willReturn(new PreviewProject.Preview(
                        "orders",
                        List.of(
                                new ProjectFile("gradle/wrapper/gradle-wrapper.jar", new byte[] {0, 1}, false),
                                new ProjectFile("gradlew", "#!/bin/sh".getBytes(StandardCharsets.UTF_8), true))));
        var result = mvc.post()
                .uri("/preview")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("name=orders")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.root").isEqualTo("orders");
        assertThat(result).bodyJson().extractingPath("$.files[0].size").isEqualTo(2);
        assertThat(result).bodyJson().doesNotHavePath("$.files[0].content");
        assertThat(result).bodyJson().extractingPath("$.files[1].content").isEqualTo("#!/bin/sh");
        assertThat(result).bodyJson().extractingPath("$.files[1].executable").isEqualTo(true);
    }

    @Test
    void previewRejectsWhatTheZipRejects() {
        var result = mvc.post()
                .uri("/preview")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("name=Orders")
                .exchange();
        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void aGenerationOverTheLimitIsTurnedAwayAtOnce() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(generateProject.generate(any())).willAnswer(invocation -> {
            inside.countDown();
            release.await();
            return new ProjectArchive("demo.zip", new byte[] {1});
        });
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first =
                    executor.submit(() -> mvc.get().uri("/starter.zip").exchange());
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
            var second = mvc.get().uri("/starter.zip").exchange();
            assertThat(second).hasStatus(503).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(second).headers().hasValue("Retry-After", "1");
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        }
    }
}
