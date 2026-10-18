package [[packageName]].ping.application.service;

import [[packageName]].ping.application.port.in.Ping;
import [[packageName]].ping.domain.Pong;
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
