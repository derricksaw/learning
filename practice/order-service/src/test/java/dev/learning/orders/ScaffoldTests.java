package dev.learning.orders;

import dev.learning.orders.support.PostgresTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldTests extends PostgresTestSupport {
    @Test
    void readsInventoryFromPostgres() throws Exception {
        var response = request("GET", "/inventory/1", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) field(response, "$.available")).isEqualTo(3);
    }

    @Test
    void readsAnExistingOrder() throws Exception {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO purchase_order (id, product_id, quantity, status) VALUES (?, 1, 2, 'PLACED')", id);
        var response = request("GET", "/orders/" + id, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) field(response, "$.id")).isEqualTo(id.toString());
        assertThat((Integer) field(response, "$.quantity")).isEqualTo(2);
    }

    @Test
    void rejectsMissingAndInvalidRequestFields() throws Exception {
        for (String body : new String[]{"{}", "{\"productId\":1,\"quantity\":0}",
                "{\"productId\":1,\"quantity\":-1}", "{\"productId\":-1,\"quantity\":2}", "not-json"}) {
            var response = request("POST", "/orders", body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat((String) field(response, "$.code")).isEqualTo("INVALID_REQUEST");
        }
        assertThat(stock()).isEqualTo(3);
        assertThat(orderCount()).isZero();
    }

    @Test
    void reportsMissingResources() throws Exception {
        assertThat(request("GET", "/inventory/999", null).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/orders/" + UUID.randomUUID(), null).statusCode()).isEqualTo(404);
        assertThat(request("POST", "/orders", "{\"productId\":999,\"quantity\":2}").statusCode()).isEqualTo(404);
        assertThat(orderCount()).isZero();
    }

    @Test
    void schemaRejectsInvalidStoredValues() {
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory SET available = -1 WHERE product_id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO purchase_order (id, product_id, quantity, status) VALUES (?, 1, 0, 'PLACED')",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(stock()).isEqualTo(3);
        assertThat(orderCount()).isZero();
    }
}
