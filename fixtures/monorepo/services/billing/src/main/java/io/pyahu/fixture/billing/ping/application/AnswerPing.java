package io.pyahu.fixture.billing.ping.application;

import io.pyahu.fixture.billing.ping.domain.Pong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Answers a ping. */
@Service
public class AnswerPing {

    private static final Logger log = LoggerFactory.getLogger(AnswerPing.class);

    public Pong ping() {
        log.info("ping");
        return Pong.reply();
    }
}
