package dev.learning.orders.invoicing;

import dev.learning.orders.service.OrderProblem;
import dev.learning.orders.service.OrderService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InvoiceService {
    private final OrderService orders;
    private final InvoiceRepository invoices;
    private final TransactionTemplate transactions;

    public InvoiceService(OrderService orders, InvoiceRepository invoices, TransactionTemplate transactions) {
        this.orders = orders;
        this.invoices = invoices;
        this.transactions = transactions;
    }

    public InvoiceView createOrGet(UUID orderId) {
        return transactions.execute(status -> {
            var order = orders.find(orderId);
            var existing = invoices.findByOrder(orderId);
            if (existing.isPresent()) {
                return existing.get();
            }
            // Demonstration price: MYR 10 per ordered unit; not a production pricing model.
            var candidate = new InvoiceView(UUID.randomUUID(), orderId, order.quantity() * 1000L, "MYR");
            invoices.insertIfAbsent(candidate);
            // A fresh Read Committed statement sees the winner after a conflicting insert completes.
            return invoices.findByOrder(orderId).orElseThrow(() ->
                    new IllegalStateException("Invoice insert returned without a stored invoice"));
        });
    }

    public InvoiceView find(UUID orderId) {
        orders.find(orderId);
        return invoices.findByOrder(orderId).orElseThrow(() -> OrderProblem.missing("Invoice"));
    }
}
