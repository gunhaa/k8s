package dev.k8slab.payment.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 결제 상태를 Pod 메모리에만 보관한다.
 * Pod가 재시작되면 사라지고 replica끼리 공유되지 않는다. 이 한계를 학습하려고 일부러 이렇게 만들었다.
 */
@Component
public class PaymentStore {

    private static final int MAX_SIZE = 1_000;

    private final Map<String, Payment> payments = new ConcurrentHashMap<>();

    public void save(Payment payment) {
        if (payments.size() >= MAX_SIZE) {
            evictOldest();
        }
        payments.put(payment.id(), payment);
    }

    public Optional<Payment> find(String id) {
        return Optional.ofNullable(payments.get(id));
    }

    public List<Payment> recent(int limit) {
        return payments.values().stream()
                .sorted(Comparator.comparing(Payment::requestedAt).reversed())
                .limit(limit)
                .toList();
    }

    /** 결과 이벤트 반영. 이 Pod가 접수하지 않은 결제라면(다른 replica가 접수) 새로 만든다. */
    public void applyResult(Payment result) {
        payments.merge(result.id(), result,
                (existing, incoming) -> existing.withResult(incoming.status(), incoming.balanceAfter(), incoming.reason()));
    }

    public void markFailed(String id, String reason) {
        payments.computeIfPresent(id, (k, p) -> p.withResult(PaymentStatus.FAILED, null, reason));
    }

    private void evictOldest() {
        payments.values().stream()
                .min(Comparator.comparing(Payment::requestedAt))
                .ifPresent(p -> payments.remove(p.id()));
    }
}
