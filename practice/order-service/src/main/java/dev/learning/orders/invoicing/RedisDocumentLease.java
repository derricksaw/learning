package dev.learning.orders.invoicing;

import dev.learning.orders.service.OrderProblem;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Best-effort duplicate-work suppression; database uniqueness protects durable results. */
@Component
public class RedisDocumentLease {
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisDocumentLease(StringRedisTemplate redis,
            @Value("${invoice.document.lease-millis:30000}") long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("lease duration must be positive");
        }
        this.redis = redis;
        this.ttl = Duration.ofMillis(ttlMillis);
    }

    public Optional<Lease> tryAcquire(UUID invoiceId) {
        String key = "lock:invoice-document:" + invoiceId;
        String token = UUID.randomUUID().toString();
        try {
            return acquire(key, token) ? Optional.of(new Lease(key, token)) : Optional.empty();
        } catch (DataAccessException error) {
            throw unavailable();
        }
    }

    private boolean acquire(String key, String token) {
        // TODO (exercise 005): set this key only if absent, with token and ttl in ONE Redis operation.
        // Return true only if acquired. Do not split SET and expiry into separate commands.
        throw new OrderProblem(HttpStatus.NOT_IMPLEMENTED, "EXERCISE_INCOMPLETE",
                "Exercise 005: implement Redis lease acquisition");
    }

    private void release(String key, String token) {
        // TODO (exercise 005): atomically delete only if the stored token still equals this owner token.
        // Use a Redis Lua script; an expired/missing or differently owned key must remain untouched.
        // Separate GET and DELETE commands, or an unconditional DELETE, are unsafe.
        throw new OrderProblem(HttpStatus.NOT_IMPLEMENTED, "EXERCISE_INCOMPLETE",
                "Exercise 005: implement owner-checked lease release");
    }

    private OrderProblem unavailable() {
        return new OrderProblem(HttpStatus.SERVICE_UNAVAILABLE, "COORDINATION_UNAVAILABLE",
                "Document coordination is unavailable; try again later");
    }

    public final class Lease implements AutoCloseable {
        private final String key;
        private final String token;

        private Lease(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            try {
                release(key, token);
            } catch (DataAccessException error) {
                throw unavailable();
            }
        }
    }
}
