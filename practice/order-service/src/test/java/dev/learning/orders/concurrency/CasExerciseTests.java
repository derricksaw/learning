package dev.learning.orders.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(20)
class CasExerciseTests {
    @Test
    void rejectsInsufficientCapacityAndAllowsExactFit() {
        var capacity = new LocalCapacity(3);
        assertThat(capacity.tryClaim(4)).isEmpty();
        assertThat(capacity.remaining()).isEqualTo(3);
        try (var claim = capacity.tryClaim(3).orElseThrow()) {
            assertThat(capacity.remaining()).isZero();
            assertThat(capacity.tryClaim(1)).isEmpty();
        }
        assertThat(capacity.remaining()).isEqualTo(3);
        assertThat(new LocalCapacity(0).tryClaim(1)).isEmpty();
    }

    @Test
    void closesOnlyOnceAndReturnsCapacityAfterAnException() {
        var capacity = new LocalCapacity(3);
        var claim = capacity.tryClaim(2).orElseThrow();
        claim.close();
        claim.close();
        assertThat(capacity.remaining()).isEqualTo(3);
        assertThatThrownBy(() -> {
            try (var ignored = capacity.tryClaim(2).orElseThrow()) {
                throw new IllegalStateException("downstream failure");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(capacity.remaining()).isEqualTo(3);
    }

    @Test
    void concurrentClaimsNeverOversubscribeAndCanBeReused() throws Exception {
        for (int round = 0; round < 20; round++) {
            var capacity = new LocalCapacity(7);
            var ready = new CountDownLatch(32);
            var start = new CountDownLatch(1);
            List<LocalCapacity.Claim> accepted = new ArrayList<>();
            try (var workers = Executors.newFixedThreadPool(32)) {
                List<Future<Optional<LocalCapacity.Claim>>> results = new ArrayList<>();
                for (int i = 0; i < 32; i++) {
                    results.add(workers.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("start gate timed out");
                        }
                        return capacity.tryClaim(2);
                    }));
                }
                try {
                    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                } finally {
                    start.countDown();
                }
                // Keep claims open until all decisions finish, avoiding permit recycling.
                for (var result : results) {
                    result.get(5, TimeUnit.SECONDS).ifPresent(accepted::add);
                }
                assertThat(accepted).hasSize(3);
                assertThat(capacity.remaining()).isEqualTo(1);
                // Race repeated closes too: each claim must restore its units exactly once.
                List<Future<?>> closes = new ArrayList<>();
                for (var claim : accepted) {
                    for (int repeat = 0; repeat < 4; repeat++) {
                        closes.add(workers.submit(claim::close));
                    }
                }
                for (var close : closes) {
                    close.get(5, TimeUnit.SECONDS);
                }
                assertThat(capacity.remaining()).isEqualTo(7);
                try (var reused = capacity.tryClaim(7).orElseThrow()) {
                    assertThat(capacity.remaining()).isZero();
                }
                assertThat(capacity.remaining()).isEqualTo(7);
            } finally {
                accepted.forEach(LocalCapacity.Claim::close);
            }
        }
    }
}
