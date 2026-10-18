package [[packageName]].ping.adapter.in.web;

import [[packageName]].ping.adapter.in.web.api.PingApi;
import [[packageName]].ping.adapter.in.web.api.model.PingResponse;
import [[packageName]].ping.application.port.in.Ping;
import [[packageName]].ping.domain.Pong;
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
        return ResponseEntity.ok(new PingResponse(pong.message()[[#postgres]], pong.count()[[/postgres]]));
    }
}
