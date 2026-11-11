package dev.learning.orders.repository;

import dev.learning.orders.model.OrderView;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepository {
    private final JdbcTemplate jdbc;

    public void insert(OrderView order) {
        jdbc.update("INSERT INTO purchase_order (id, product_id, quantity, status) VALUES (?, ?, ?, ?)",
                order.id(), order.productId(), order.quantity(), order.status());
    }

    public Optional<OrderView> find(UUID orderId) {
        return jdbc.query("SELECT id, product_id, quantity, status FROM purchase_order WHERE id = ?",
                (row, index) -> new OrderView(row.getObject("id", UUID.class), row.getLong("product_id"),
                        row.getInt("quantity"), row.getString("status")), orderId).stream().findFirst();
    }
}
