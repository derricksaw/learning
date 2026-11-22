package dev.learning.orders.invoicing;

import dev.learning.orders.service.OrderProblem;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Best-effort duplicate-work suppression; database uniqueness protects durable
 * results.
 */
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
        return redis.opsForValue().setIfAbsent(key, token, ttl);
    }

    private void release(String key, String token) {
        if (!redis.compareAndDelete(key, token)) {
            Logger.getLogger(this.getClass().getName()).warning("Lease not released: key is absent or ownership no longer matches");
        }
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
