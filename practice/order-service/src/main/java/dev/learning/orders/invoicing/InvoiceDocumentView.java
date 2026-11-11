package dev.learning.orders.invoicing;

import java.util.UUID;

/** Demonstration text document, not a legal invoice PDF. */
public record InvoiceDocumentView(UUID invoiceId, UUID documentId, String content) {}
