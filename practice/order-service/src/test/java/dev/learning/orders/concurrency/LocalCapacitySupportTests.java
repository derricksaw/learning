package dev.learning.orders.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalCapacitySupportTests {
    @Test
    void validatesCapacityAndClaimSize() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalCapacity(-1));
        var capacity = new LocalCapacity(3);
        assertThat(capacity.remaining()).isEqualTo(3);
        assertThatIllegalArgumentException().isThrownBy(() -> capacity.tryClaim(0));
        assertThatIllegalArgumentException().isThrownBy(() -> capacity.tryClaim(-1));
        assertThat(capacity.remaining()).isEqualTo(3);
    }
}
