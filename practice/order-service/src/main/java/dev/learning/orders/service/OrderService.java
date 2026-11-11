package dev.learning.orders.service;

import dev.learning.orders.model.OrderView;
import dev.learning.orders.repository.InventoryRepository;
import dev.learning.orders.repository.OrderRepository;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {
    private final InventoryRepository inventory;
    private final OrderRepository orders;
    private final OrderWriteObserver observer;
    private final TransactionTemplate transactionTemplate;

    public OrderService(InventoryRepository inventory, OrderRepository orders,
            ObjectProvider<OrderWriteObserver> observers, TransactionTemplate transactionTemplate) {
        this.inventory = inventory;
        this.orders = orders;
        this.observer = observers.getIfAvailable(() -> () -> {
        });
        this.transactionTemplate = transactionTemplate;
    }

    public OrderView place(long productId, int quantity) {
        if (productId <= 0 || quantity <= 0) {
            throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "IDs and quantity must be positive");
        }
        return transactionTemplate.execute(status -> {
            inventory.find(productId).orElseThrow(() -> OrderProblem.missing("Product"));
            int changed = inventory.reserveStock(productId, quantity);
            if (changed != 1) {
                throw new OrderProblem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Not enough stock for this order");
            }
            observer.afterStockReserved();
            var order = new OrderView(UUID.randomUUID(), productId, quantity, "PLACED");
            orders.insert(order);
            return order;
        });
    }

    public OrderView find(UUID orderId) {
        return orders.find(orderId).orElseThrow(() -> OrderProblem.missing("Order"));
    }
}
