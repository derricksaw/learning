package dev.learning.orders.service;

import dev.learning.orders.model.AdjustmentView;
import dev.learning.orders.repository.AdjustmentRepository;
import dev.learning.orders.repository.InventoryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AdjustmentService {
    private final InventoryRepository inventory;
    private final AdjustmentRepository adjustments;
    private final TransactionTemplate transactionTemplate;

    public AdjustmentView adjust(long productId, int delta, String reason) {
        if (productId <= 0 || delta == 0) {
            throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Use a positive product ID and nonzero delta");
        }
        return transactionTemplate.execute(status -> {
            var current = inventory.findForAdjustment(productId)
                    .orElseThrow(() -> OrderProblem.missing("Product"));
            long next = (long) current.available() + delta;
            if (next < 0) {
                throw new OrderProblem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Adjustment would make stock negative");
            }
            if (next > Integer.MAX_VALUE) {
                throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Stock exceeds the supported range");
            }
            if (inventory.setAvailable(productId, (int) next) != 1) {
                throw OrderProblem.missing("Product");
            }
            var adjustment = new AdjustmentView(UUID.randomUUID(), productId, delta,
                    current.available(), (int) next, reason.trim());
            adjustments.insert(adjustment);
            return adjustment;
        });
    }

    public AdjustmentView find(UUID id) {
        return adjustments.find(id).orElseThrow(() -> OrderProblem.missing("Adjustment"));
    }
}
