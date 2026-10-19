package io.pyahu.start.generation.presentation;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * At most a few generations at once. Each one holds a whole project in memory; virtual threads would otherwise
 * take every request the server accepts. A request over the limit gets a 503 at once instead of waiting; the rate per
 * client is limited at the edge.
 */
@Component
class GenerationLimit {

    /** Thrown when every permit is taken. */
    static final class Busy extends RuntimeException {
        Busy() {
            super("too many generations at once", null, false, false);
        }
    }

    private final Semaphore permits;

    GenerationLimit(@Value("${start.generation.max-concurrent:16}") int maxConcurrent) {
        this.permits = new Semaphore(maxConcurrent);
    }

    <T> T run(Supplier<T> generation) {
        if (!permits.tryAcquire()) {
            throw new Busy();
        }
        try {
            return generation.get();
        } finally {
            permits.release();
        }
    }
}
