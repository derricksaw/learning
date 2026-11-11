package dev.learning.orders.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {
    @NotNull @Positive
    private Long productId;

    @NotNull @Positive
    private Integer quantity;
}
