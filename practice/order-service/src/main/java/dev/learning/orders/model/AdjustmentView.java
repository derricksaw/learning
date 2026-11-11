package dev.learning.orders.model;

import java.util.UUID;

public record AdjustmentView(UUID id, long productId, int delta,
                             int beforeAvailable, int afterAvailable, String reason) {}
