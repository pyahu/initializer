package [[packageName]].ping.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import [[packageName]].ping.application.port.in.Ping;
import [[packageName]].ping.domain.Pong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PingController.class)
class PingControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    Ping ping;

    @Test
    void answersPong() {
        given(ping.ping()).willReturn(new Pong("pong"));
        assertThat(mvc.get().uri("/ping"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo("pong");
    }

    @Test
    void answersAnUnknownPathWithAProblemDetail() {
        assertThat(mvc.get().uri("/nothing-here")).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
