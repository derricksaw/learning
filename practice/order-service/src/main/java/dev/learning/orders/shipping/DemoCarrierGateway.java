package dev.learning.orders.shipping;

import dev.learning.orders.model.OrderView;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local simulator: performs no network request and purchases no postage. */
@Component
public class DemoCarrierGateway implements CarrierGateway {
    private final long delayMillis;

    public DemoCarrierGateway(@Value("${shipping.demo-delay-millis:1000}") long delayMillis) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("carrier delay must be nonnegative");
        }
        this.delayMillis = delayMillis;
    }

    @Override
    public ShippingLabelView createLabel(OrderView order) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new Unavailable("Carrier simulation interrupted");
        }
        return new ShippingLabelView(order.id(), "DEMO-" + UUID.randomUUID(), true);
    }
}
