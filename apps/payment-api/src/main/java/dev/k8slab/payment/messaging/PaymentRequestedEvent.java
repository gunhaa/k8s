package dev.k8slab.payment.messaging;

import java.time.Instant;

/** payment.requested 토픽 메시지 계약 (balance-worker와는 JSON 필드명으로만 약속한다) */
public record PaymentRequestedEvent(
        String paymentId,
        String userId,
        String type,
        long amount,
        Instant requestedAt) {
}
