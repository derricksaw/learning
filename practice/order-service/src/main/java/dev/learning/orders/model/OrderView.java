package dev.learning.orders.model;

import java.util.UUID;

public record OrderView(UUID id, long productId, int quantity, String status) {}
