package dev.learning.orders.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReplaceStockRequest(@NotNull @Min(0) Integer available,
        @NotNull @Min(0) Long expectedVersion) {}
