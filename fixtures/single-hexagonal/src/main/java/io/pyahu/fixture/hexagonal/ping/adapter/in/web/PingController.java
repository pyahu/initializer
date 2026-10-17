package io.pyahu.fixture.hexagonal.ping.adapter.in.web;

import io.pyahu.fixture.hexagonal.ping.application.port.in.Ping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PingController {

    private final Ping ping;

    PingController(Ping ping) {
        this.ping = ping;
    }

    @GetMapping("/ping")
    String ping() {
        return ping.ping().message();
    }
}
