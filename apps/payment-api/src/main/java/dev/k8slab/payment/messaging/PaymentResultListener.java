package dev.k8slab.payment.messaging;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import dev.k8slab.payment.domain.Payment;
import dev.k8slab.payment.domain.PaymentStatus;
import dev.k8slab.payment.domain.PaymentStore;
import dev.k8slab.payment.domain.PaymentType;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final PaymentStore store;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public PaymentResultListener(PaymentStore store, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    // groupId가 Pod마다 다르므로(POD_NAME) 각 Pod가 모든 결과 이벤트를 받는다.
    // 새로 뜬 Pod는 과거 이벤트가 필요 없으므로 latest부터 읽는다.
    @KafkaListener(
            topics = "${app.topics.result}",
            groupId = "${app.result-group-id}",
            properties = "auto.offset.reset=latest")
    public void onResult(String message) {
        PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
        PaymentStatus status = PaymentStatus.valueOf(event.status());

        store.applyResult(new Payment(event.paymentId(), event.userId(), PaymentType.valueOf(event.type()),
                event.amount(), status, event.balanceAfter(), event.reason(), event.requestedAt(), Instant.now()));

        meterRegistry.counter("payments.results", "status", status.name()).increment();
        log.info("payment {} -> {} (balanceAfter={})", event.paymentId(), status, event.balanceAfter());
    }
}
