package dev.learning.orders.workspace;

import dev.learning.orders.service.OrderProblem;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Working, deliberately inefficient baselines for production query exercises. */
@Repository
public class OrderHistoryRepository {
    private final JdbcTemplate jdbc;

    public OrderHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(UUID id, long productId, String productName, int quantity, String status,
            OffsetDateTime createdAt, boolean hasInvoice) {}
    public record ProductReport(long productId, String productName, long orders, long units,
            long invoices, long billedMinor) {}

    private static final RowMapper<Row> ROW = (row, index) -> new Row(
            row.getObject("id", UUID.class), row.getLong("product_id"), row.getString("product_name"),
            row.getInt("quantity"), row.getString("status"),
            row.getObject("created_at", OffsetDateTime.class), row.getBoolean("has_invoice"));

    public List<Row> offsetPage(int fetchSize, int offset, Long productId,
            LocalDate from, LocalDate to, String query) {
        // TODO 007: explain this complete statement and record its plan before changing it.
        // TODO 008: improve measured filter/search/order access paths while preserving UTC-day semantics.
        // TODO 010: evaluate bounded related-data retrieval without dropping or duplicating parent orders.
        // The casted correlated count deliberately preserves the original exercise's slow baseline.
        var sql = new StringBuilder("""
                SELECT o.id, o.product_id, i.product_name, o.quantity, o.status, o.created_at,
                       (SELECT count(*) FROM order_invoice v
                        WHERE v.order_id::text = o.id::text) > 0 AS has_invoice
                FROM purchase_order o JOIN inventory i ON i.product_id = o.product_id
                WHERE 1 = 1
                """);
        var parameters = new ArrayList<Object>();
        if (productId != null) {
            sql.append(" AND o.product_id = ?");
            parameters.add(productId);
        }
        if (from != null) {
            sql.append(" AND (o.created_at AT TIME ZONE 'UTC')::date >= ?");
            parameters.add(from);
        }
        if (to != null) {
            sql.append(" AND (o.created_at AT TIME ZONE 'UTC')::date <= ?");
            parameters.add(to);
        }
        if (!query.isEmpty()) {
            sql.append(" AND position(lower(?) in lower(i.product_name)) > 0");
            parameters.add(query);
        }
        sql.append(" ORDER BY o.created_at DESC, o.id ASC LIMIT ? OFFSET ?");
        parameters.add(fetchSize);
        parameters.add(offset);
        return jdbc.query(sql.toString(), ROW, parameters.toArray());
    }

    public List<Row> cursorPage(int fetchSize, OffsetDateTime afterCreatedAt, UUID afterId,
            Long productId, LocalDate from, LocalDate to, String query) {
        // TODO 009: implement first/next cursor pages with identical filters and mixed-direction ordering.
        // Keep the page's parent identities stable before enriching them with invoice information.
        throw new OrderProblem(HttpStatus.NOT_IMPLEMENTED, "EXERCISE_INCOMPLETE",
                "Exercise 009: implement cursor pagination in OrderHistoryRepository.cursorPage");
    }

    public List<ProductReport> productReport() {
        // TODO 011: reduce repeated report work; preserve one row per product, zero totals and invoice sums.
        return jdbc.query("""
                SELECT i.product_id, i.product_name,
                       (SELECT count(*) FROM purchase_order o WHERE o.product_id = i.product_id) AS orders,
                       (SELECT coalesce(sum(o.quantity), 0) FROM purchase_order o
                        WHERE o.product_id = i.product_id) AS units,
                       (SELECT count(*) FROM order_invoice v JOIN purchase_order o ON o.id = v.order_id
                        WHERE o.product_id = i.product_id) AS invoices,
                       (SELECT coalesce(sum(v.total_minor), 0) FROM order_invoice v
                        JOIN purchase_order o ON o.id = v.order_id
                        WHERE o.product_id = i.product_id) AS billed_minor
                FROM inventory i ORDER BY i.product_id LIMIT 100
                """, (row, index) -> new ProductReport(row.getLong("product_id"), row.getString("product_name"),
                        row.getLong("orders"), row.getLong("units"), row.getLong("invoices"),
                        row.getLong("billed_minor")));
    }
}
