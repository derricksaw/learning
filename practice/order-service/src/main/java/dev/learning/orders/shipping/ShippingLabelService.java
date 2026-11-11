package dev.learning.orders.shipping;

import dev.learning.orders.concurrency.LocalCapacity;
import dev.learning.orders.concurrency.LocalCapacity.Claim;
import dev.learning.orders.model.OrderView;
import dev.learning.orders.service.OrderProblem;
import dev.learning.orders.service.OrderService;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ShippingLabelService {
    private final OrderService orders;
    private final CarrierGateway carrier;
    private final LocalCapacity capacity;

    public ShippingLabelService(OrderService orders, CarrierGateway carrier,
            @Value("${shipping.max-concurrent:2}") int maxConcurrent) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("carrier capacity must be positive");
        }
        this.orders = orders;
        this.carrier = carrier;
        this.capacity = new LocalCapacity(maxConcurrent);
    }

    public ShippingLabelView create(UUID orderId) {
        var order = orders.find(orderId);
        return createWithCapacity(order);
    }

    private ShippingLabelView createWithCapacity(OrderView order) {
        Optional<Claim> claim = capacity.tryClaim(1);
        if (claim.isEmpty()) {
            throw new OrderProblem(HttpStatus.SERVICE_UNAVAILABLE, "CARRIER_BUSY", "Carrier could not create a label");
        }
        try (Claim c = claim.get()) {
            return callCarrier(order);
        }
    }

    private ShippingLabelView callCarrier(OrderView order) {
        try {
            return carrier.createLabel(order);
        } catch (CarrierGateway.Unavailable error) {
            throw new OrderProblem(HttpStatus.BAD_GATEWAY, "CARRIER_UNAVAILABLE", "Carrier could not create a label");
        }
    }
}
