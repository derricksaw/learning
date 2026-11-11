package dev.learning.orders.invoicing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import dev.learning.orders.service.OrderProblem;

@Repository
public class InvoiceRepository {
    private final JdbcTemplate jdbc;

    public InvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<InvoiceView> findByOrder(UUID orderId) {
        return jdbc.query("SELECT id, order_id, total_minor, currency FROM order_invoice WHERE order_id = ?",
                (row, index) -> new InvoiceView(row.getObject("id", UUID.class),
                        row.getObject("order_id", UUID.class), row.getLong("total_minor"), row.getString("currency")),
                orderId).stream().findFirst();
    }

    public int insertIfAbsent(InvoiceView candidate) {
        // TODO (exercise 005): insert the candidate once, using the unique order_id as business identity.
        // Return 1 when inserted, 0 when this order already has an invoice; never overwrite it.
        // The insert must resolve a concurrent duplicate atomically in PostgreSQL.
        throw new OrderProblem(HttpStatus.NOT_IMPLEMENTED, "EXERCISE_INCOMPLETE",
                "Exercise 005: implement durable invoice deduplication");
    }
}
