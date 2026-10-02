package dev.k8slab.payment.api;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import dev.k8slab.payment.domain.Payment;
import dev.k8slab.payment.domain.PaymentStatus;
import dev.k8slab.payment.domain.PaymentStore;
import dev.k8slab.payment.messaging.PaymentRequestedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentStore store;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String requestedTopic;

    public PaymentService(PaymentStore store, KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper, MeterRegistry meterRegistry,
            @Value("${app.topics.requested}") String requestedTopic) {
        this.store = store;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.requestedTopic = requestedTopic;
    }

    /**
     * 1) 인메모리에 ACCEPTED로 저장 → 2) Kafka 전송은 기다리지 않음(비동기) → 3) 바로 반환.
     * DB 쓰기는 balance-worker가 이벤트를 받아서 처리한다.
     */
    public Payment request(PaymentRequest request) {
        Instant now = Instant.now();
        Payment payment = new Payment(UUID.randomUUID().toString(), request.userId(), request.type(),
                request.amount(), PaymentStatus.ACCEPTED, null, null, now, now);
        store.save(payment);

        PaymentRequestedEvent event = new PaymentRequestedEvent(payment.id(), payment.userId(),
                payment.type().name(), payment.amount(), now);

        // key = userId → 같은 사용자의 이벤트는 같은 파티션에 들어가 순서가 보장된다
        kafkaTemplate.send(requestedTopic, payment.userId(), objectMapper.writeValueAsString(event))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("failed to publish payment {}", payment.id(), ex);
                        store.markFailed(payment.id(), "kafka publish failed: " + ex.getMessage());
                    } else {
                        log.debug("published payment {} to partition {}", payment.id(),
                                result.getRecordMetadata().partition());
                    }
                });

        meterRegistry.counter("payments.requested", "type", payment.type().name()).increment();
        return payment;
    }
}
