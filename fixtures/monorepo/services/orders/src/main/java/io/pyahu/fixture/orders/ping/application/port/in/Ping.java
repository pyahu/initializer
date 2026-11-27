package io.pyahu.fixture.orders.ping.application.port.in;

import io.pyahu.fixture.orders.ping.domain.Pong;

/** Answers a ping. */
public interface Ping {

    Pong ping();
}
