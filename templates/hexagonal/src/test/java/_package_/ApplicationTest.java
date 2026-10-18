package [[packageName]];

import static org.assertj.core.api.Assertions.assertThat;

import [[packageName]].ping.application.port.in.Ping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTest {

    @Autowired
    Ping ping;

    @Test
    void contextStartsAndAnswers() {
        assertThat(ping.ping().message()).isEqualTo("pong");
    }
}
