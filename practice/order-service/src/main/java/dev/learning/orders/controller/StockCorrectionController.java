package dev.learning.orders.controller;

import dev.learning.orders.model.InventoryView;
import dev.learning.orders.service.StockCorrectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory/{productId}/stock")
public class StockCorrectionController {
    private final StockCorrectionService corrections;

    public StockCorrectionController(StockCorrectionService corrections) {
        this.corrections = corrections;
    }

    @PutMapping
    public InventoryView replace(@PathVariable long productId, @Valid @RequestBody ReplaceStockRequest request) {
        return corrections.replace(productId, request.available(), request.expectedVersion());
    }
}
