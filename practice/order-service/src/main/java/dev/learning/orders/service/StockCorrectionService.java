package dev.learning.orders.service;

import dev.learning.orders.model.InventoryView;
import dev.learning.orders.repository.InventoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StockCorrectionService {
    private final InventoryRepository inventory;
    private final TransactionTemplate transactions;

    public StockCorrectionService(InventoryRepository inventory, TransactionTemplate transactions) {
        this.inventory = inventory;
        this.transactions = transactions;
    }

    public InventoryView replace(long productId, int available, long expectedVersion) {
        if (productId <= 0 || available < 0 || expectedVersion < 0) {
            throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Use a positive product ID and nonnegative stock and version");
        }
        return transactions.execute(status -> {
            inventory.find(productId).orElseThrow(() -> OrderProblem.missing("Product"));
            if (inventory.replaceIfVersionMatches(productId, available, expectedVersion) != 1) {
                throw new OrderProblem(HttpStatus.CONFLICT, "STALE_INVENTORY",
                        "Inventory changed since it was read; reload and reconsider the correction");
            }
            return inventory.find(productId).orElseThrow(() -> OrderProblem.missing("Product"));
        });
    }
}
