package [[packageName]].ping.presentation;

import [[packageName]].ping.application.AnswerPing;
import [[packageName]].ping.domain.Pong;
import [[packageName]].ping.presentation.api.PingApi;
import [[packageName]].ping.presentation.api.model.PingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated interface and maps the domain answer to the contract's model. */
@RestController
class PingController implements PingApi {

    private final AnswerPing answerPing;

    PingController(AnswerPing answerPing) {
        this.answerPing = answerPing;
    }

    @Override
    public ResponseEntity<PingResponse> ping() {
        Pong pong = answerPing.ping();
        return ResponseEntity.ok(new PingResponse(pong.message()[[#postgres]], pong.count()[[/postgres]]));
    }
}
