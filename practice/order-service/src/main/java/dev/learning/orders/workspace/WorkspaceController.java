package dev.learning.orders.workspace;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read models for the operations console; mutations use the existing exercise services. */
@RestController
@RequestMapping("/workspace")
public class WorkspaceController {
    private final JdbcTemplate jdbc;

    public WorkspaceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Item(long productId, String name, String category, int available, int reorderPoint, long version) {}
    public record Summary(long orders, long units, long lowStock, long invoices) {}
    public record Order(UUID id, long productId, String productName, int quantity, String status,
            OffsetDateTime createdAt, boolean hasInvoice) {}
    public record Invoice(UUID id, UUID orderId, String productName, long totalMinor, String currency,
            boolean hasDocument) {}

    @GetMapping("/summary")
    public Summary summary() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM purchase_order) AS orders,
                       (SELECT coalesce(sum(available), 0) FROM inventory) AS units,
                       (SELECT count(*) FROM inventory WHERE available <= reorder_point) AS low_stock,
                       (SELECT count(*) FROM order_invoice) AS invoices
                """, (row, index) -> new Summary(row.getLong("orders"), row.getLong("units"),
                        row.getLong("low_stock"), row.getLong("invoices")));
    }

    @GetMapping("/inventory")
    public List<Item> inventory() {
        return jdbc.query("SELECT * FROM inventory ORDER BY product_id",
                (row, index) -> new Item(row.getLong("product_id"), row.getString("product_name"),
                        row.getString("category"), row.getInt("available"), row.getInt("reorder_point"),
                        row.getLong("version")));
    }

    @GetMapping("/orders")
    public List<Order> orders() {
        return jdbc.query("""
                SELECT o.*, i.product_name, EXISTS (SELECT 1 FROM order_invoice v WHERE v.order_id = o.id) AS has_invoice
                FROM purchase_order o JOIN inventory i ON i.product_id = o.product_id
                ORDER BY o.created_at DESC, o.id LIMIT 100
                """, (row, index) -> new Order(row.getObject("id", UUID.class), row.getLong("product_id"),
                        row.getString("product_name"), row.getInt("quantity"), row.getString("status"),
                        row.getObject("created_at", OffsetDateTime.class), row.getBoolean("has_invoice")));
    }

    @GetMapping("/invoices")
    public List<Invoice> invoices() {
        return jdbc.query("""
                SELECT v.*, i.product_name, EXISTS (SELECT 1 FROM invoice_document d WHERE d.invoice_id = v.id) AS has_document
                FROM order_invoice v JOIN purchase_order o ON o.id = v.order_id
                JOIN inventory i ON i.product_id = o.product_id ORDER BY o.created_at DESC, v.id LIMIT 100
                """, (row, index) -> new Invoice(row.getObject("id", UUID.class), row.getObject("order_id", UUID.class),
                        row.getString("product_name"), row.getLong("total_minor"), row.getString("currency"),
                        row.getBoolean("has_document")));
    }
}
