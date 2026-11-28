package io.pyahu.fixture.billing;

import static org.assertj.core.api.Assertions.assertThat;

import io.pyahu.fixture.billing.ping.application.AnswerPing;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("integration")
class ApplicationTest {

    @Autowired
    AnswerPing answerPing;

    @Test
    void contextStartsAndAnswers() {
        assertThat(answerPing.ping().message()).isEqualTo("pong");
    }
}
