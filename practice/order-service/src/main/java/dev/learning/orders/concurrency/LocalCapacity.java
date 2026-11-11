package dev.learning.orders.concurrency;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-local admission budget; never a replacement for database stock. */
public final class LocalCapacity {
    private final AtomicInteger remaining;

    public LocalCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be nonnegative");
        }
        remaining = new AtomicInteger(capacity);
    }

    public int remaining() {
        return remaining.get();
    }

    /** Returns empty immediately when insufficient capacity exists. */
    public Optional<Claim> tryClaim(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
        for (;;) {
            int remainingSnapshot = remaining();
            int remainingAfterClaimed = remainingSnapshot - units;
            if (remainingAfterClaimed < 0) {
                break;
            }
            if (remaining.compareAndSet(remainingSnapshot, remainingAfterClaimed)) {
                return Optional.of(new Claim(units));
            }
        }
        return Optional.empty();
    }

    /**
     * Use try-with-resources so completion and exceptions return a successful
     * claim.
     */
    public final class Claim implements AutoCloseable {
        private final int units;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Claim(int units) {
            this.units = units;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                remaining.addAndGet(units);
            }
        }
    }
}
