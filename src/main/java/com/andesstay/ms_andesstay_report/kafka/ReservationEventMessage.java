package com.andesstay.ms_andesstay_report.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO propio del evento de reservations.events (contrato JSON publicado por ms-andesstay-reservations).
 */
public record ReservationEventMessage(
        String eventId,
        String eventType,
        Instant occurredAt,
        Long reservationId,
        String previousStatus,
        String newStatus,
        String actor,
        String alojamientoNombre,
        Long unidadId,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        BigDecimal precioTotal,
        String source
) {
}
