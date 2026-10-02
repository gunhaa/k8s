package dev.k8slab.payment.messaging;

import java.time.Instant;

/** payment.result 토픽 메시지 계약 */
public record PaymentResultEvent(
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
