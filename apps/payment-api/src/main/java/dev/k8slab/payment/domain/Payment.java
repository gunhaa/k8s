package dev.k8slab.payment.domain;

import java.time.Instant;

public record Payment(
        String id,
        String userId,
        PaymentType type,
        long amount,
        PaymentStatus status,
        Long balanceAfter,
        String reason,
        Instant requestedAt,
        Instant updatedAt) {

    public Payment withResult(PaymentStatus newStatus, Long newBalanceAfter, String newReason) {
        return new Payment(id, userId, type, amount, newStatus, newBalanceAfter, newReason, requestedAt, Instant.now());
    }
}
