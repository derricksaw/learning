package dev.learning.orders;

import dev.learning.orders.support.PostgresTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdjustmentSupportTests extends PostgresTestSupport {
    @Test
    void readsAnExistingAuditRecord() throws Exception {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_adjustment
                    (id, product_id, delta, before_available, after_available, reason)
                VALUES (?, 1, -2, 3, 1, 'damaged stock')
                """, id);
        var response = request("GET", "/inventory/adjustments/" + id, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) field(response, "$.beforeAvailable")).isEqualTo(3);
        assertThat((Integer) field(response, "$.afterAvailable")).isEqualTo(1);
    }

    @Test
    void rejectsInvalidAdjustmentInputBeforeReachingTheGap() throws Exception {
        for (String body : new String[]{"{}", "{\"delta\":0,\"reason\":\"recount\"}",
                "{\"delta\":-2,\"reason\":\"  \"}"}) {
            assertThat(request("POST", "/inventory/1/adjustments", body).statusCode()).isEqualTo(400);
        }
        assertThat(stock()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_adjustment", Integer.class)).isZero();
    }
}
