package dev.learning.orders.repository;

import dev.learning.orders.model.AdjustmentView;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdjustmentRepository {
    private final JdbcTemplate jdbc;

    public void insert(AdjustmentView adjustment) {
        jdbc.update("""
                INSERT INTO inventory_adjustment
                    (id, product_id, delta, before_available, after_available, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, adjustment.id(), adjustment.productId(), adjustment.delta(),
                adjustment.beforeAvailable(), adjustment.afterAvailable(), adjustment.reason());
    }

    public Optional<AdjustmentView> find(UUID id) {
        return jdbc.query("SELECT * FROM inventory_adjustment WHERE id = ?",
                (row, index) -> new AdjustmentView(row.getObject("id", UUID.class), row.getLong("product_id"),
                        row.getInt("delta"), row.getInt("before_available"),
                        row.getInt("after_available"), row.getString("reason")), id).stream().findFirst();
    }
}
