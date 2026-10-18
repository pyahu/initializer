package [[packageName]].ping.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
[[#oidc]]
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
[[/oidc]]

[[#oidc]]
import [[packageName]].config.SecurityConfig;
[[/oidc]]
import [[packageName]].ping.application.port.in.Ping;
import [[packageName]].ping.domain.Pong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
[[#oidc]]
import org.springframework.context.annotation.Import;
[[/oidc]]
[[^oidc]]
import org.springframework.http.MediaType;
[[/oidc]]
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PingController.class)
[[#oidc]]
@Import(SecurityConfig.class)
[[/oidc]]
class PingControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    Ping ping;

    @Test
    void answersPong() {
        given(ping.ping()).willReturn(new Pong("pong"[[#postgres]], 1[[/postgres]]));
        String body = "{\"message\":\"pong\"[[#postgres]],\"count\":1[[/postgres]]}";
        assertThat(mvc.get().uri("/ping")[[#oidc]].with(jwt())[[/oidc]]).hasStatusOk().bodyJson().isLenientlyEqualTo(body);
    }
[[^oidc]]

    @Test
    void answersAnUnknownPathWithAProblemDetail() {
        assertThat(mvc.get().uri("/nothing-here")).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
[[/oidc]]
[[#oidc]]

    @Test
    void refusesARequestWithoutAToken() {
        assertThat(mvc.get().uri("/ping")).hasStatus(401);
    }

    @Test
    void deniesAPathNobodyAllowed() {
        assertThat(mvc.get().uri("/nothing-here").with(jwt())).hasStatus(403);
    }
[[/oidc]]
}
