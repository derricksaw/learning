package dev.learning.orders.controller;

import dev.learning.orders.invoicing.InvoiceDocumentService;
import dev.learning.orders.invoicing.InvoiceDocumentView;
import dev.learning.orders.invoicing.InvoiceService;
import dev.learning.orders.invoicing.InvoiceView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders/{orderId}/invoice")
public class InvoiceController {
    private final InvoiceService invoices;
    private final InvoiceDocumentService documents;

    public InvoiceController(InvoiceService invoices, InvoiceDocumentService documents) {
        this.invoices = invoices;
        this.documents = documents;
    }

    @PostMapping
    public InvoiceView create(@PathVariable UUID orderId) {
        return invoices.createOrGet(orderId);
    }

    @GetMapping
    public InvoiceView find(@PathVariable UUID orderId) {
        return invoices.find(orderId);
    }

    @PostMapping("/document")
    public InvoiceDocumentView document(@PathVariable UUID orderId) {
        return documents.createOrGet(orderId);
    }
}
