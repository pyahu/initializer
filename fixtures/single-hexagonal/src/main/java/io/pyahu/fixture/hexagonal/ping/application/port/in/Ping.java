package io.pyahu.fixture.hexagonal.ping.application.port.in;

import io.pyahu.fixture.hexagonal.ping.domain.Pong;

/** Answers a ping. */
public interface Ping {

    Pong ping();
}
