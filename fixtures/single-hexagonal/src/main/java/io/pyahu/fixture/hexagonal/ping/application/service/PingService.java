package io.pyahu.fixture.hexagonal.ping.application.service;

import io.pyahu.fixture.hexagonal.ping.application.port.in.Ping;
import io.pyahu.fixture.hexagonal.ping.domain.Pong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class PingService implements Ping {

    private static final Logger log = LoggerFactory.getLogger(PingService.class);

    @Override
    public Pong ping() {
        log.info("ping");
        return Pong.reply();
    }
}
