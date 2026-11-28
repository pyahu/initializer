package io.pyahu.fixture.orders.ping.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The use case as plain Java, with a fake in place of what it calls (testing guide, step 2). */
class PingServiceTest {

    @Test
    void answersPong() {
        var pong = new PingService(() -> 7).ping();

        assertThat(pong.message()).isEqualTo("pong");
        assertThat(pong.count()).isEqualTo(7);
    }
}
