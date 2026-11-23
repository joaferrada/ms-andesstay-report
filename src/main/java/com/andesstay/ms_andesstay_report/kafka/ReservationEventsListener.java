package com.andesstay.ms_andesstay_report.kafka;

import com.andesstay.ms_andesstay_report.service.StreamingKpiService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumidor de reservations.events (grupo report-group, distinto de audit-group: ambos reciben
 * todos los eventos). Delega la lógica en {@link StreamingKpiService}.
 *
 * Los KPIs viven en memoria: al asignarse cada partición se vuelve al inicio del tópico y se
 * reconstruye el estado desde el log (los eventos se conservan 7 días). Los duplicados se
 * descartan por eventId, así que un rebalanceo no cuenta dos veces.
 */
@Component
public class ReservationEventsListener extends AbstractConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventsListener.class);

    private final StreamingKpiService kpiService;

    public ReservationEventsListener(StreamingKpiService kpiService) {
        this.kpiService = kpiService;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        super.onPartitionsAssigned(assignments, callback);
        log.info("[KAFKA-REPLAY] report reconstruye KPIs desde el inicio de {}", assignments.keySet());
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(
            id = "report-reservation-events",
            topics = "${andesstay.kafka.reservation-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onReservationEvent(ConsumerRecord<String, ReservationEventMessage> record) {
        ReservationEventMessage event = record.value();
        log.info("[KAFKA-CONSUME] topic={} partition={} offset={} key={} eventId={} type={}",
                record.topic(), record.partition(), record.offset(), record.key(),
                event == null ? null : event.eventId(), event == null ? null : event.eventType());
        kpiService.onReservationEvent(event);
    }
}
