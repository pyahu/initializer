package [[packageName]].ping.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The use case as plain Java, with a fake in place of what it calls (testing guide, step 2). */
class AnswerPingTest {

    @Test
    void answersPong() {
[[^postgres]]
        assertThat(new AnswerPing().ping().message()).isEqualTo("pong");
[[/postgres]]
[[#postgres]]
        var pong = new AnswerPing(() -> 7).ping();

        assertThat(pong.message()).isEqualTo("pong");
        assertThat(pong.count()).isEqualTo(7);
[[/postgres]]
    }
}
