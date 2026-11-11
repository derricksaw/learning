package dev.learning.orders;

import dev.learning.orders.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceApplicationTests extends PostgresTestSupport {
    @Test
    void servesHealthOverHttp() throws Exception {
        var response = request("GET", "/actuator/health", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) field(response, "$.status")).isEqualTo("UP");
    }
}
