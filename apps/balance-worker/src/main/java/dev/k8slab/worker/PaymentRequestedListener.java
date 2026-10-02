package dev.k8slab.worker;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedListener.class);

    private final BalanceProcessor processor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String resultTopic;

    public PaymentRequestedListener(BalanceProcessor processor, KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper, MeterRegistry meterRegistry,
            @Value("${app.topics.result}") String resultTopic) {
        this.processor = processor;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.resultTopic = resultTopic;
    }

    @KafkaListener(topics = "${app.topics.requested}")
    public void onRequested(ConsumerRecord<String, String> record)
            throws ExecutionException, InterruptedException, TimeoutException {
        // JSON이 깨진 메시지는 JacksonException → 재시도 없이 DLT로 간다 (KafkaConfig 참고)
        PaymentEvents.Requested event = objectMapper.readValue(record.value(), PaymentEvents.Requested.class);

        PaymentEvents.Result result = processor.process(event);

        // DB 커밋 이후 결과 발행. 동기로 기다려야 발행 실패 시 offset이 커밋되지 않고 재처리된다.
        kafkaTemplate.send(resultTopic, event.userId(), objectMapper.writeValueAsString(result))
                .get(10, TimeUnit.SECONDS);

        meterRegistry.counter("payments.processed", "status", result.status()).increment();
        log.info("processed payment {} user={} {} {} -> {} (partition={}, offset={})",
                event.paymentId(), event.userId(), event.type(), event.amount(), result.status(),
                record.partition(), record.offset());
    }
}
