package [[packageName]].ping.application;

[[#postgres]]
import [[packageName]].ping.domain.PingLog;
[[/postgres]]
import [[packageName]].ping.domain.Pong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
[[#postgres]]
import org.springframework.transaction.annotation.Transactional;
[[/postgres]]

/** Answers a ping. */
@Service
public class AnswerPing {

    private static final Logger log = LoggerFactory.getLogger(AnswerPing.class);
[[#postgres]]

    private final PingLog pingLog;

    AnswerPing(PingLog pingLog) {
        this.pingLog = pingLog;
    }
[[/postgres]]

[[#postgres]]
    @Transactional
[[/postgres]]
    public Pong ping() {
        log.info("ping");
        return Pong.reply([[#postgres]]pingLog.record()[[/postgres]]);
    }
}
