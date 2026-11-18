package dev.learning.orders.packing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPackingRepository implements PackingRepository {
    private final JdbcTemplate jdbc;

    JdbcPackingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PackingOrder> find(UUID orderId) {
        return jdbc.query("""
                SELECT o.id, o.quantity, COALESCE(p.packed_quantity, 0) AS packed_quantity
                FROM purchase_order o
                LEFT JOIN packing_progress p ON p.order_id = o.id
                WHERE o.id = ?
                """, (row, index) -> new PackingOrder(row.getObject("id", UUID.class),
                        row.getInt("quantity"), row.getInt("packed_quantity")), orderId)
                .stream().findFirst();
    }

    @Override
    public Optional<PackingOrder> findForUpdate(UUID orderId) {
        var locked = jdbc.query("SELECT id FROM purchase_order WHERE id = ? FOR UPDATE",
                (row, index) -> row.getObject("id", UUID.class), orderId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        // A separate statement reads progress after any competing writer commits.
        return find(orderId);
    }

    @Override
    public void save(PackingOrder order) {
        jdbc.update("""
                INSERT INTO packing_progress (order_id, packed_quantity) VALUES (?, ?)
                ON CONFLICT (order_id) DO UPDATE SET packed_quantity = EXCLUDED.packed_quantity
                """, order.orderId(), order.packedQuantity());
    }
}
