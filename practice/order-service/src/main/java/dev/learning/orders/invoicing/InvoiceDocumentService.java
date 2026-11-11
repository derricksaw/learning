package dev.learning.orders.invoicing;

import dev.learning.orders.service.OrderProblem;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InvoiceDocumentService {
    private final InvoiceService invoices;
    private final InvoiceDocumentRepository documents;
    private final RedisDocumentLease leases;
    private final DemoInvoiceRenderer renderer;

    public InvoiceDocumentService(InvoiceService invoices, InvoiceDocumentRepository documents,
            RedisDocumentLease leases, DemoInvoiceRenderer renderer) {
        this.invoices = invoices;
        this.documents = documents;
        this.leases = leases;
        this.renderer = renderer;
    }

    // No database transaction spans the renderer or Redis calls.
    public InvoiceDocumentView createOrGet(UUID orderId) {
        var invoice = invoices.find(orderId);
        var existing = documents.find(invoice.id());
        if (existing.isPresent()) {
            return existing.get();
        }
        try (var lease = leases.tryAcquire(invoice.id()).orElseThrow(() ->
                new OrderProblem(HttpStatus.CONFLICT, "DOCUMENT_BUSY", "This invoice document is being generated"))) {
            // Another owner may have published between our first read and acquisition.
            return documents.find(invoice.id()).orElseGet(() ->
                    documents.publishIfAbsent(renderer.render(invoice)));
        }
    }
}
