package com.andesstay.ms_andesstay_report.kafka;

import java.time.Instant;

/**
 * DTO propio de una entrada de audit.timeline (contrato JSON publicado por ms-andesstay-audit).
 */
public record TimelineEntryMessage(
        Long auditId,
        String eventId,
        Long reservationId,
        String tipo,
        String actor,
        String estadoAnterior,
        String estadoNuevo,
        String detalle,
        Instant occurredAt,
        Instant registradoEn,
        String source
) {
}
