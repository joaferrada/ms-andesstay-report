package com.andesstay.ms_andesstay_report.kafka;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * andesstay.kafka.* en application.yaml: tópicos que consume report, sus DLT y los reintentos.
 */
@Validated
@ConfigurationProperties(prefix = "andesstay.kafka")
public record ReportKafkaProperties(
        @NotBlank String reservationEventsTopic,
        @NotBlank String auditTimelineTopic,
        @Valid @NotNull DeadLetter deadLetter,
        @Valid @NotNull Retry retry) {

    /** Configuración común de los DLT de report: el nombre es el tópico original + suffix. */
    public record DeadLetter(
            @Positive int partitions,
            @Positive int replicas,
            @NotNull Duration retention,
            @Positive int minInsyncReplicas,
            @NotBlank String suffix) {

        public String topicFor(String originalTopic) {
            return originalTopic + suffix;
        }
    }

    public record Retry(
            @Positive int maxAttempts,
            @NotNull Duration initialInterval,
            @Positive double multiplier,
            @NotNull Duration maxInterval) {
    }
}
