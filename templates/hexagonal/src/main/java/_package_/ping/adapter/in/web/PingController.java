package [[packageName]].ping.adapter.in.web;

import [[packageName]].ping.adapter.in.web.api.PingApi;
import [[packageName]].ping.adapter.in.web.api.model.PingResponse;
import [[packageName]].ping.application.port.in.Ping;
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
        return ResponseEntity.ok(new PingResponse(ping.ping().message()));
    }
}
