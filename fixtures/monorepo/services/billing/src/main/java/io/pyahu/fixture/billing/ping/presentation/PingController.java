package io.pyahu.fixture.billing.ping.presentation;

import io.pyahu.fixture.api.billing.PingApi;
import io.pyahu.fixture.api.billing.model.PingResponse;
import io.pyahu.fixture.billing.ping.application.AnswerPing;
import io.pyahu.fixture.billing.ping.domain.Pong;
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
        return ResponseEntity.ok(new PingResponse(pong.message()));
    }
}
