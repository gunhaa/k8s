package dev.k8slab.payment.api;

import dev.k8slab.payment.domain.PaymentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
        @NotBlank String userId,
        @NotNull PaymentType type,
        @Positive @Max(10_000_000) long amount) {
}
