package dev.learning.orders.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustmentRequest(@NotNull Integer delta, @NotBlank @Size(max = 200) String reason) {}
