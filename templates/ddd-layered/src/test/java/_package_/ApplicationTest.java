package [[packageName]];

import static org.assertj.core.api.Assertions.assertThat;

import [[packageName]].ping.application.AnswerPing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTest {

    @Autowired
    AnswerPing answerPing;

    @Test
    void contextStartsAndAnswers() {
        assertThat(answerPing.ping().message()).isEqualTo("pong");
    }
}
