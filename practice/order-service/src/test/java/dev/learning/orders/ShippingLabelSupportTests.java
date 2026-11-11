package dev.learning.orders;

import dev.learning.orders.model.OrderView;
import dev.learning.orders.shipping.DemoCarrierGateway;
import dev.learning.orders.support.PostgresTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingLabelSupportTests extends PostgresTestSupport {
    @Test
    void missingOrderAndInvalidIdAreRejectedBeforeTheGap() throws Exception {
        var missing = request("POST", "/orders/" + UUID.randomUUID() + "/shipping-labels", null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat((String) field(missing, "$.code")).isEqualTo("NOT_FOUND");
        assertThat(request("POST", "/orders/not-a-uuid/shipping-labels", null).statusCode()).isEqualTo(400);
        assertThat(stock()).isEqualTo(3);
        assertThat(orderCount()).isZero();
    }

    @Test
    void simulatorIdentifiesItsOutputAsSimulated() {
        var order = new OrderView(UUID.randomUUID(), 1, 1, "PLACED");
        var label = new DemoCarrierGateway(0).createLabel(order);
        assertThat(label.orderId()).isEqualTo(order.id());
        assertThat(label.carrierReference()).startsWith("DEMO-");
        assertThat(label.simulated()).isTrue();
    }
}
