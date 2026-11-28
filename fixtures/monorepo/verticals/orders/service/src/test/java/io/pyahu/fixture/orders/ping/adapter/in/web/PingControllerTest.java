package io.pyahu.fixture.orders.ping.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.pyahu.fixture.orders.config.SecurityConfig;
import io.pyahu.fixture.orders.ping.application.port.in.Ping;
import io.pyahu.fixture.orders.ping.domain.Pong;
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
    Ping ping;

    @Test
    void answersPong() {
        given(ping.ping()).willReturn(new Pong("pong", 1));
        String body = "{\"message\":\"pong\",\"count\":1}";
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
