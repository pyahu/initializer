package [[packageName]].ping.application.service;

import [[packageName]].ping.application.port.in.Ping;
[[#postgres]]
import [[packageName]].ping.application.port.out.PingLog;
[[/postgres]]
import [[packageName]].ping.domain.Pong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
[[#postgres]]
import org.springframework.transaction.annotation.Transactional;
[[/postgres]]

@Service
class PingService implements Ping {

    private static final Logger log = LoggerFactory.getLogger(PingService.class);
[[#postgres]]

    private final PingLog pingLog;

    PingService(PingLog pingLog) {
        this.pingLog = pingLog;
    }
[[/postgres]]

    @Override
[[#postgres]]
    @Transactional
[[/postgres]]
    public Pong ping() {
        log.info("ping");
        return Pong.reply([[#postgres]]pingLog.record()[[/postgres]]);
    }
}
