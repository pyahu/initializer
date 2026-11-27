package io.pyahu.fixture.orders.ping.adapter.in.web;

import io.pyahu.fixture.api.orders.PingApi;
import io.pyahu.fixture.api.orders.model.PingResponse;
import io.pyahu.fixture.orders.ping.application.port.in.Ping;
import io.pyahu.fixture.orders.ping.domain.Pong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated interface and maps the domain answer to the contract's model. */
@RestController
class PingController implements PingApi {

    private final Ping ping;

    PingController(Ping ping) {
        this.ping = ping;
    }

    @Override
    public ResponseEntity<PingResponse> ping() {
        Pong pong = ping.ping();
        return ResponseEntity.ok(new PingResponse(pong.message(), pong.count()));
    }
}
