package dev.learning.orders.controller;

import dev.learning.orders.shipping.ShippingLabelService;
import dev.learning.orders.shipping.ShippingLabelView;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders/{orderId}/shipping-labels")
public class ShippingLabelController {
    private final ShippingLabelService labels;

    public ShippingLabelController(ShippingLabelService labels) {
        this.labels = labels;
    }

    @PostMapping
    public ShippingLabelView create(@PathVariable UUID orderId) {
        return labels.create(orderId);
    }
}
