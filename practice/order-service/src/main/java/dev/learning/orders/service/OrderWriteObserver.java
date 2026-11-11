package dev.learning.orders.service;

/** Internal observation point for deterministic failure tests; never controlled by HTTP input. */
@FunctionalInterface
public interface OrderWriteObserver {
    void afterStockReserved();
}
