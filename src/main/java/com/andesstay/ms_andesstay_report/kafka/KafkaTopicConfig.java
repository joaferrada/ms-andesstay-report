package com.andesstay.ms_andesstay_report.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * DLT propios de report (uno por tópico consumido). Los tópicos de origen los declaran sus dueños:
 * reservations.events (reservations) y audit.timeline (audit).
 */
@Configuration
@EnableConfigurationProperties(ReportKafkaProperties.class)
public class KafkaTopicConfig {

    @Bean
    public NewTopic reservationEventsReportDlt(ReportKafkaProperties props) {
        return deadLetter(props, props.reservationEventsTopic());
    }

    @Bean
    public NewTopic auditTimelineReportDlt(ReportKafkaProperties props) {
        return deadLetter(props, props.auditTimelineTopic());
    }

    private static NewTopic deadLetter(ReportKafkaProperties props, String originalTopic) {
        ReportKafkaProperties.DeadLetter dlt = props.deadLetter();
        return TopicBuilder.name(dlt.topicFor(originalTopic))
                .partitions(dlt.partitions())
                .replicas(dlt.replicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(dlt.retention().toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(dlt.minInsyncReplicas()))
                .build();
    }
}
