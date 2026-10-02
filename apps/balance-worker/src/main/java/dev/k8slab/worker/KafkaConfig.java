package dev.k8slab.worker;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConfig {

    /**
     * 처리 실패 시 1초 간격으로 2번 재시도한 뒤 DLT 토픽으로 보낸다.
     * 잘못된 메시지(파싱 실패, 잘못된 값)는 재시도해도 소용없으므로 바로 DLT로 보낸다.
     * Spring Boot가 이 CommonErrorHandler 빈을 기본 listener container factory에 자동 연결한다.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.topics.dead-letter}") String deadLetterTopic) {
        // partition -1 → 파티션을 지정하지 않고 Kafka가 고르게 한다
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(deadLetterTopic, -1));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2));
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }
}
