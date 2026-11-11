package dev.learning.orders.repository;

import dev.learning.orders.model.InventoryView;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryRepository {
    private final JdbcTemplate jdbc;

    public Optional<InventoryView> find(long productId) {
        return jdbc.query("SELECT product_id, available, version FROM inventory WHERE product_id = ?",
                (row, index) -> new InventoryView(row.getLong("product_id"), row.getInt("available"),
                        row.getLong("version")),
                productId).stream().findFirst();
    }

    public Optional<InventoryView> findForAdjustment(long productId) {
        return jdbc.query("SELECT product_id, available, version FROM inventory WHERE product_id = ? FOR UPDATE",
                (row, index) -> new InventoryView(row.getLong("product_id"), row.getInt("available"),
                        row.getLong("version")),
                productId).stream().findFirst();
    }

    public int setAvailable(long productId, int available) {
        return jdbc.update("UPDATE inventory SET available = ?, version = version + 1 WHERE product_id = ?", available,
                productId);
    }

    public int replaceIfVersionMatches(long productId, int available, long expectedVersion) {
        return jdbc.update(
                "UPDATE inventory SET available = ?, version = version + 1 WHERE version = ? AND product_id = ?",
                available, expectedVersion, productId);
    }

    public int reserveStock(long productId, int quantity) {
        // Returns 1 on success, 0 when availability cannot satisfy the request.
        return jdbc.update(
                "UPDATE inventory SET available = available - ?, version = version + 1 WHERE product_id = ? AND available >= ?",
                quantity, productId, quantity);
    }
}
