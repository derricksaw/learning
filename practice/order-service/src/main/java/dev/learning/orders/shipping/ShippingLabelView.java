package dev.learning.orders.shipping;

import java.util.UUID;

public record ShippingLabelView(UUID orderId, String carrierReference, boolean simulated) {}
