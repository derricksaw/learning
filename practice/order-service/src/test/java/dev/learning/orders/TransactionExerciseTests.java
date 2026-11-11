package dev.learning.orders;

import dev.learning.orders.service.OrderWriteObserver;
import dev.learning.orders.support.PostgresTestSupport;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TransactionExerciseTests.FailureConfiguration.class)
class TransactionExerciseTests extends PostgresTestSupport {
    @Autowired
    FailureSwitch failure;

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void clearFailure() {
        failure.armed.set(false);
    }

    @Test
    void placesOrderAndPersistsTheExactDeduction() throws Exception {
        var response = request("POST", "/orders", "{\"productId\":1,\"quantity\":2}");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat((Integer) field(response, "$.quantity")).isEqualTo(2);
        assertThat(stock()).isEqualTo(1);
        assertThat(orderCount()).isEqualTo(1);
        String id = field(response, "$.id");
        assertThat(request("GET", "/orders/" + id, null).statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsInsufficientStockWithoutWritingAnOrder() throws Exception {
        var response = request("POST", "/orders", "{\"productId\":1,\"quantity\":4}");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(stock()).isEqualTo(3);
        assertThat(orderCount()).isZero();
    }

    @Test
    void rollsBackStockWhenTheNextStepFails() throws Exception {
        failure.armed.set(true);
        var response = request("POST", "/orders", "{\"productId\":1,\"quantity\":2}");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat((String) field(response, "$.code")).isEqualTo("INTERNAL_ERROR");
        assertThat(stock()).isEqualTo(3);
        assertThat(orderCount()).isZero();
    }

    @Test
    void competingRequestsCannotOversell() throws Exception {
        // Hold the stock row so competing requests reach their writes before either can finish.
        // This makes a stale read-then-set implementation fail deterministically.
        try (var blocker = dataSource.getConnection(); var pool = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT product_id FROM inventory WHERE product_id = 1 FOR UPDATE")) {
                lock.executeQuery().close();
            }
            var first = pool.submit(() -> request("POST", "/orders", "{\"productId\":1,\"quantity\":2}").statusCode());
            var second = pool.submit(() -> request("POST", "/orders", "{\"productId\":1,\"quantity\":2}").statusCode());
            boolean observedContention = false;
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline && !(first.isDone() && second.isDone())) {
                    Integer waiters = jdbc.queryForObject("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database() AND wait_event_type = 'Lock'
                              AND pid <> pg_backend_pid()
                            """, Integer.class);
                    if (waiters >= 2) {
                        observedContention = true;
                        break;
                    }
                    Thread.sleep(25);
                }
            } finally {
                blocker.commit();
            }
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(observedContention).as("both database operations must contend before release").isTrue();
        }
        assertThat(stock()).isEqualTo(1);
        assertThat(orderCount()).isEqualTo(1);
    }

    static class FailureSwitch implements OrderWriteObserver {
        final AtomicBoolean armed = new AtomicBoolean();

        @Override
        public void afterStockReserved() {
            if (armed.compareAndSet(true, false)) {
                throw new IllegalStateException("Controlled test-only failure after stock reservation");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureConfiguration {
        @Bean
        FailureSwitch failureSwitch() {
            return new FailureSwitch();
        }
    }
}
