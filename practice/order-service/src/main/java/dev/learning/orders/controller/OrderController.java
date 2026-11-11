package dev.learning.orders.controller;

import dev.learning.orders.model.OrderView;
import dev.learning.orders.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(@Valid @RequestBody CreateOrderRequest request) {
        return orders.place(request.getProductId(), request.getQuantity());
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable UUID orderId) {
        return orders.find(orderId);
    }
}
