package dev.learning.orders.controller;

import dev.learning.orders.model.InventoryView;
import dev.learning.orders.model.AdjustmentView;
import dev.learning.orders.service.AdjustmentService;
import jakarta.validation.Valid;
import java.util.UUID;
import dev.learning.orders.repository.InventoryRepository;
import dev.learning.orders.service.OrderProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryRepository inventory;
    private final AdjustmentService adjustments;

    @PostMapping("/{productId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentView adjust(@PathVariable long productId, @Valid @RequestBody AdjustmentRequest request) {
        return adjustments.adjust(productId, request.delta(), request.reason());
    }

    @GetMapping("/adjustments/{adjustmentId}")
    public AdjustmentView adjustment(@PathVariable UUID adjustmentId) {
        return adjustments.find(adjustmentId);
    }

    @GetMapping("/{productId}")
    public InventoryView get(@PathVariable long productId) {
        if (productId <= 0) {
            throw new OrderProblem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "productId must be positive");
        }
        return inventory.find(productId).orElseThrow(() -> OrderProblem.missing("Product"));
    }
}
