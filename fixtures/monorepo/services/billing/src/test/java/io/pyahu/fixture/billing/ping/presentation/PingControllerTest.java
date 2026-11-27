package io.pyahu.fixture.billing.ping.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.pyahu.fixture.billing.config.SecurityConfig;
import io.pyahu.fixture.billing.ping.application.AnswerPing;
import io.pyahu.fixture.billing.ping.domain.Pong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PingController.class)
@Import(SecurityConfig.class)
class PingControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AnswerPing answerPing;

    @Test
    void answersPong() {
        given(answerPing.ping()).willReturn(new Pong("pong"));
        String body = "{\"message\":\"pong\"}";
        assertThat(mvc.get().uri("/ping").with(jwt())).hasStatusOk().bodyJson().isLenientlyEqualTo(body);
    }

    @Test
    void refusesARequestWithoutAToken() {
        assertThat(mvc.get().uri("/ping")).hasStatus(401);
    }

    @Test
    void deniesAPathNobodyAllowed() {
        assertThat(mvc.get().uri("/nothing-here").with(jwt())).hasStatus(403);
    }
}
