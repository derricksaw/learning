package dev.learning.orders.shipping;

import dev.learning.orders.model.OrderView;

public interface CarrierGateway {
    ShippingLabelView createLabel(OrderView order);

    class Unavailable extends RuntimeException {
        public Unavailable(String message) {
            super(message);
        }
    }
}
