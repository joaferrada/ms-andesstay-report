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
 * Consumidor de audit.timeline (segundo tópico, grupo report-group). El JSON se lee como
 * {@link TimelineEntryMessage}: el tipo por defecto del deserializador se sobrescribe aquí.
 */
@Component
public class AuditTimelineListener extends AbstractConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(AuditTimelineListener.class);

    private final StreamingKpiService kpiService;

    public AuditTimelineListener(StreamingKpiService kpiService) {
        this.kpiService = kpiService;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        super.onPartitionsAssigned(assignments, callback);
        log.info("[KAFKA-REPLAY] report reconstruye la actividad desde el inicio de {}", assignments.keySet());
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(
            id = "report-audit-timeline",
            topics = "${andesstay.kafka.audit-timeline-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.andesstay.ms_andesstay_report.kafka.TimelineEntryMessage")
    public void onTimelineEntry(ConsumerRecord<String, TimelineEntryMessage> record) {
        TimelineEntryMessage entry = record.value();
        log.info("[KAFKA-CONSUME] topic={} partition={} offset={} key={} auditId={} tipo={}",
                record.topic(), record.partition(), record.offset(), record.key(),
                entry == null ? null : entry.auditId(), entry == null ? null : entry.tipo());
        kpiService.onTimelineEntry(entry);
    }
}
