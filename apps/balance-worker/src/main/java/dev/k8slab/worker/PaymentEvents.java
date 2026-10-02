package dev.k8slab.worker;

import java.time.Instant;

/** payment-api와 JSON 필드명으로만 약속한 메시지 계약 */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    public record Requested(
            String paymentId,
            String userId,
            String type,
            long amount,
            Instant requestedAt) {
    }

    public record Result(
            String paymentId,
            String userId,
            String type,
            long amount,
            String status,
            Long balanceAfter,
            String reason,
            Instant requestedAt,
            Instant processedAt) {
    }
}
