package dev.learning.orders.invoicing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
        return jdbc.update(
                "INSERT INTO order_invoice (id, order_id, total_minor, currency) VALUES (?, ?, ?, ?) ON CONFLICT (order_id) DO NOTHING",
                candidate.id(), candidate.orderId(), candidate.totalMinor(), candidate.currency());
    }
}
