package dev.learning.orders.invoicing;

import java.util.UUID;

public record InvoiceView(UUID id, UUID orderId, long totalMinor, String currency) {}
