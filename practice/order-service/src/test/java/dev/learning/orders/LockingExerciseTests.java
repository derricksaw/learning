package dev.learning.orders;

import dev.learning.orders.repository.InventoryRepository;
import dev.learning.orders.support.PostgresTestSupport;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class LockingExerciseTests extends PostgresTestSupport {
    @Autowired
    DataSource dataSource;

    @Autowired
    InventoryRepository inventory;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void recordsAdjustmentFromTheProtectedBalance() throws Exception {
        var response = request("POST", "/inventory/1/adjustments", "{\"delta\":-2,\"reason\":\"damaged stock\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat((Integer) field(response, "$.beforeAvailable")).isEqualTo(3);
        assertThat((Integer) field(response, "$.afterAvailable")).isEqualTo(1);
        assertThat(stock()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void rejectsImpossibleAdjustmentAndMissingProduct() throws Exception {
        assertThat(request("POST", "/inventory/1/adjustments", "{\"delta\":-4,\"reason\":\"damaged\"}").statusCode())
                .isEqualTo(409);
        assertThat(request("POST", "/inventory/999/adjustments", "{\"delta\":1,\"reason\":\"delivery\"}").statusCode())
                .isEqualTo(404);
        assertThat(stock()).isEqualTo(3);
        assertThat(auditCount()).isZero();
    }

    @Test
    void lockingReadWaitsForAnExistingWriter() throws Exception {
        try (var blocker = dataSource.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            blocker.createStatement().execute("UPDATE inventory SET available = 3 WHERE product_id = 1");
            var read = pool.submit(() -> transactionTemplate.execute(status -> inventory.findForAdjustment(1)));
            boolean observedWait = false;
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (!read.isDone() && System.nanoTime() < deadline) {
                    if (waitingTransactions() >= 1) {
                        observedWait = true;
                        break;
                    }
                    Thread.sleep(20);
                }
            } finally {
                blocker.commit();
            }
            assertThat(observedWait).as("the read must wait before returning a decision-making balance").isTrue();
            assertThat(read.get(5, TimeUnit.SECONDS)).isPresent();
        }
    }

    @Test
    void twoAdjustmentsCannotReuseTheSameBalance() throws Exception {
        assertCompetingRequests("/inventory/1/adjustments", "{\"delta\":-2,\"reason\":\"damaged\"}");
        assertThat(stock()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
        assertThat(orderCount()).isZero();
    }

    @Test
    void adjustmentCoordinatesWithExistingOrderReservations() throws Exception {
        assertCompetingRequests("/orders", "{\"productId\":1,\"quantity\":2}");
        assertThat(stock()).isEqualTo(1);
        assertThat(auditCount() + orderCount()).isEqualTo(1);
    }

    private void assertCompetingRequests(String otherPath, String otherBody) throws Exception {
        try (var blocker = dataSource.getConnection(); var pool = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT product_id FROM inventory WHERE product_id = 1 FOR UPDATE")) {
                lock.executeQuery().close();
            }
            var first = pool.submit(() -> request("POST", "/inventory/1/adjustments",
                    "{\"delta\":-2,\"reason\":\"recount\"}").statusCode());
            var second = pool.submit(() -> request("POST", otherPath, otherBody).statusCode());
            boolean observedContention = false;
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (!(first.isDone() && second.isDone()) && System.nanoTime() < deadline) {
                    if (waitingTransactions() >= 2) {
                        observedContention = true;
                        break;
                    }
                    Thread.sleep(20);
                }
            } finally {
                blocker.commit();
            }
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(observedContention).as("both operations must contend on the inventory row").isTrue();
        }
    }

    private int waitingTransactions() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()
                """, Integer.class);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_adjustment", Integer.class);
    }
}
