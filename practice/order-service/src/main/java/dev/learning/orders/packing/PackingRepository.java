package dev.learning.orders.packing;

import java.util.Optional;
import java.util.UUID;

interface PackingRepository {
    Optional<PackingOrder> find(UUID orderId);

    // Call inside the same transaction as save, even when no progress row exists.
    Optional<PackingOrder> findForUpdate(UUID orderId);

    void save(PackingOrder order);
}
