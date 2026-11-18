package dev.learning.orders.packing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class PackingService {
    private final PackingRepository orders;
    private final TransactionTemplate transactions;

    PackingService(PackingRepository orders, TransactionTemplate transactions) {
        this.orders = orders;
        this.transactions = transactions;
    }

    Optional<PackingOrder> find(UUID orderId) {
        return orders.find(orderId);
    }

    Optional<PackingOrder> record(UUID orderId, int total) {
        return transactions.execute(status -> orders.findForUpdate(orderId).map(current -> {
            var updated = current.recordPackedQuantity(total);
            if (!updated.equals(current)) {
                orders.save(updated);
            }
            return updated;
        }));
    }
}
