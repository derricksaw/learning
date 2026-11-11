package dev.learning.orders;

import dev.learning.orders.support.PostgresTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingLabelHttpExerciseTests extends PostgresTestSupport {
    @Test
    void existingOrderCanRequestASimulatedLabelWithoutChangingStock() throws Exception {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO purchase_order (id, product_id, quantity, status) VALUES (?, 1, 1, 'PLACED')", id);
        var response = request("POST", "/orders/" + id + "/shipping-labels", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) field(response, "$.orderId")).isEqualTo(id.toString());
        assertThat((Boolean) field(response, "$.simulated")).isTrue();
        assertThat((String) field(response, "$.carrierReference")).startsWith("DEMO-");
        assertThat(stock()).isEqualTo(3);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id))
                .isEqualTo("PLACED");
    }
}
