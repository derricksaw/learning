package dev.learning.orders.packing;

import java.util.UUID;

public record PackingOrder(UUID orderId, int orderedQuantity, int packedQuantity) {
    public PackingOrder recordPackedQuantity(int total) {
        // TODO(014): Enforce the packing invariant and return the resulting aggregate.
        throw new Unfinished();
    }

    static final class Unfinished extends RuntimeException {
        Unfinished() {
            super("Packing transition is unfinished");
        }
    }

    public static final class Rejected extends RuntimeException {
        public Rejected(String message) {
            super(message);
        }
    }
}
