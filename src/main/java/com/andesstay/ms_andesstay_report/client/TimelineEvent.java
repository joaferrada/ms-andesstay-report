package com.andesstay.ms_andesstay_report.client;

import java.time.Instant;

/**
 * Evento del timeline de ms-andesstay-audit (solo los campos que usan los KPIs).
 *
 * @param tipo RESERVA_CREADA, CAMBIO_ESTADO o RESERVA_ELIMINADA
 */
public record TimelineEvent(
        String tipo,
        String estadoNuevo,
        Instant occurredAt) {
}
