package io.pyahu.fixture.hexagonal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.EnableResilientMethods;

class ConcurrencyLimitsTest {

    @Test
    void classLimitRejectsAnotherMethodWithoutQueueing() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var context = new AnnotationConfigApplicationContext(Clients.class);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var client = context.getBean(Client.class);
            var first = executor.submit(() -> client.first(entered, release));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                var excess = executor.submit(client::second);
                assertThatThrownBy(() -> excess.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(InvocationRejectedException.class);
            } finally {
                release.countDown();
            }
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(client.second()).isEqualTo("second");
        }
    }

    @Test
    void cpuPoolRejectsWhenItsWorkersAndQueueAreFull() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy())) {
            try {
                pool.execute(() -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                pool.execute(() -> {});
                assertThatThrownBy(() -> pool.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
                assertThat(pool.getQueue()).hasSize(1);
            } finally {
                release.countDown();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableResilientMethods
    static class Clients {
        @Bean
        Client client() {
            return new Client();
        }
    }

    @ConcurrencyLimit(limit = 1, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
    static class Client {
        public String first(CountDownLatch entered, CountDownLatch release) throws InterruptedException {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "first";
        }

        public String second() {
            return "second";
        }
    }
}
