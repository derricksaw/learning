package dev.learning.orders;

import dev.learning.orders.model.OrderView;
import dev.learning.orders.service.OrderProblem;
import dev.learning.orders.service.OrderService;
import dev.learning.orders.shipping.CarrierGateway;
import dev.learning.orders.shipping.ShippingLabelService;
import dev.learning.orders.shipping.ShippingLabelView;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(15)
class ShippingLabelExerciseTests {
    private final OrderView order = new OrderView(UUID.randomUUID(), 1, 1, "PLACED");

    private ShippingLabelService service(CarrierGateway gateway, int limit) {
        var orders = mock(OrderService.class);
        when(orders.find(any(UUID.class))).thenReturn(order);
        return new ShippingLabelService(orders, gateway, limit);
    }

    @Test
    void successfulCallsReturnTheirCapacity() {
        var calls = new AtomicInteger();
        var service = service(found -> {
            calls.incrementAndGet();
            return new ShippingLabelView(found.id(), "DEMO-test", true);
        }, 1);
        for (int i = 0; i < 3; i++) {
            assertThat(service.create(order.id()).orderId()).isEqualTo(order.id());
        }
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void carrierFailureIsMappedAndReturnsCapacity() {
        var attempts = new AtomicInteger();
        var service = service(found -> {
            if (attempts.getAndIncrement() == 0) {
                throw new CarrierGateway.Unavailable("upstream failed");
            }
            return new ShippingLabelView(found.id(), "DEMO-recovered", true);
        }, 1);
        assertThatThrownBy(() -> service.create(order.id()))
                .isInstanceOfSatisfying(OrderProblem.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(error.getCode()).isEqualTo("CARRIER_UNAVAILABLE");
                });
        assertThat(service.create(order.id()).carrierReference()).isEqualTo("DEMO-recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void unexpectedFailureAlsoReturnsCapacity() {
        var attempts = new AtomicInteger();
        var service = service(found -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("unexpected client failure");
            }
            return new ShippingLabelView(found.id(), "DEMO-recovered", true);
        }, 1);
        assertThatThrownBy(() -> service.create(order.id())).isInstanceOf(IllegalStateException.class);
        assertThat(service.create(order.id()).carrierReference()).isEqualTo("DEMO-recovered");
    }

    @Test
    void twoCallsOverlapAndThirdIsRejectedWithoutCallingCarrier() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var service = service(found -> {
            calls.incrementAndGet();
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test carrier timed out");
                }
                return new ShippingLabelView(found.id(), "DEMO-concurrent", true);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            } finally {
                active.decrementAndGet();
            }
        }, 2);
        try (var workers = Executors.newFixedThreadPool(3)) {
            var first = workers.submit(() -> service.create(order.id()));
            var second = workers.submit(() -> service.create(order.id()));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS))
                        .as("two carrier calls should enter concurrently").isTrue();
                // Submit separately so a wrongly blocking admission decision is bounded too.
                var third = workers.submit(() -> {
                    assertThatThrownBy(() -> service.create(order.id()))
                            .isInstanceOfSatisfying(OrderProblem.class, error -> {
                                assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                                assertThat(error.getCode()).isEqualTo("CARRIER_BUSY");
                            });
                });
                third.get(1, TimeUnit.SECONDS);
                assertThat(calls.get()).isEqualTo(2);
                assertThat(maximum.get()).isEqualTo(2);
            } finally {
                release.countDown();
            }
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertThat(service.create(order.id()).simulated()).isTrue();
            assertThat(active.get()).isZero();
            assertThat(calls.get()).isEqualTo(3);
        }
    }
}
