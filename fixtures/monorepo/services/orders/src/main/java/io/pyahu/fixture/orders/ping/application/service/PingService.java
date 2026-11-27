package io.pyahu.fixture.orders.ping.application.service;

import io.pyahu.fixture.orders.ping.application.port.in.Ping;
import io.pyahu.fixture.orders.ping.application.port.out.PingLog;
import io.pyahu.fixture.orders.ping.domain.Pong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PingService implements Ping {

    private static final Logger log = LoggerFactory.getLogger(PingService.class);

    private final PingLog pingLog;

    PingService(PingLog pingLog) {
        this.pingLog = pingLog;
    }

    @Override
    @Transactional
    public Pong ping() {
        log.info("ping");
        return Pong.reply(pingLog.record());
    }
}
