package dev.learning.orders.packing;

import dev.learning.orders.service.OrderProblem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders/{orderId}/packing")
class PackingController {
    private final PackingService packing;

    PackingController(PackingService packing) {
        this.packing = packing;
    }

    record RecordPackingRequest(@NotNull Integer packedQuantity) {}

    record PackingView(UUID orderId, int orderedQuantity, int packedQuantity, boolean complete) {
        static PackingView from(PackingOrder order) {
            return new PackingView(order.orderId(), order.orderedQuantity(), order.packedQuantity(),
                    order.packedQuantity() == order.orderedQuantity());
        }
    }

    @GetMapping
    PackingView get(@PathVariable UUID orderId) {
        return packing.find(orderId).map(PackingView::from)
                .orElseThrow(() -> OrderProblem.missing("Order"));
    }

    @PutMapping
    PackingView record(@PathVariable UUID orderId, @Valid @RequestBody RecordPackingRequest request) {
        try {
            return packing.record(orderId, request.packedQuantity()).map(PackingView::from)
                    .orElseThrow(() -> OrderProblem.missing("Order"));
        } catch (PackingOrder.Unfinished error) {
            throw new OrderProblem(HttpStatus.NOT_IMPLEMENTED, "PACKING_UNFINISHED", error.getMessage());
        } catch (PackingOrder.Rejected error) {
            throw new OrderProblem(HttpStatus.CONFLICT, "PACKING_REJECTED", error.getMessage());
        }
    }
}
