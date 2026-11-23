package com.andesstay.ms_andesstay_report.kafka;

import com.andesstay.ms_andesstay_report.exception.InvalidStreamEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Manejo de errores de los consumidores de report: reintentos con backoff exponencial y,
 * si no se puede procesar, publicación en &lt;tópico&gt;.report.DLT. El consumidor sigue con el siguiente.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ProducerFactory<?, ?> producerFactory, ReportKafkaProperties props) {
        ReportKafkaProperties.DeadLetter dlt = props.deadLetter();

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterTemplate(producerFactory),
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(dlt.topicFor(record.topic()), record.partition()));

        ReportKafkaProperties.Retry retry = props.retry();
        ExponentialBackOff backOff = new ExponentialBackOff(retry.initialInterval().toMillis(), retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());
        backOff.setMaxAttempts(retry.maxAttempts());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidStreamEventException.class, IllegalArgumentException.class);
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                log.warn("[KAFKA-RETRY] intento={} topic={} partition={} offset={} key={}: {}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(), record.key(),
                        ex.getMessage());
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                log.error("[KAFKA-DLT] enviado a {} topic={} partition={} offset={} key={}: {}",
                        dlt.topicFor(record.topic()), record.topic(), record.partition(), record.offset(),
                        record.key(), ex.getMessage());
            }
        });
        return handler;
    }

    private static KafkaTemplate<String, Object> deadLetterTemplate(ProducerFactory<?, ?> producerFactory) {
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(
                producerFactory.getConfigurationProperties(), new StringSerializer(), new DeadLetterValueSerializer());
        return new KafkaTemplate<>(factory);
    }

    /** byte[] (JSON que no se pudo leer) se copia tal cual; cualquier otro objeto se serializa a JSON. */
    static final class DeadLetterValueSerializer implements Serializer<Object> {

        private final JsonMapper mapper = JsonMapper.builder().build();

        @Override
        public byte[] serialize(String topic, Object data) {
            if (data == null) {
                return null;
            }
            if (data instanceof byte[] bytes) {
                return bytes;
            }
            return mapper.writeValueAsBytes(data);
        }
    }
}
