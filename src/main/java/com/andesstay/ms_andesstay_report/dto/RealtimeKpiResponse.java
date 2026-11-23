package com.andesstay.ms_andesstay_report.dto;

import com.andesstay.ms_andesstay_report.kafka.TimelineEntryMessage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * KPIs calculados en tiempo real a partir de los tópicos reservations.events y audit.timeline.
 *
 * @param reservasPorEstado      estado actual de cada reserva conocida (las eliminadas no cuentan)
 * @param reservasCreadasPorHora hora local (America/Santiago) -> reservas creadas, últimas 24 horas con datos
 * @param ingresosConfirmados    suma de precioTotal de las reservas que pasaron a CONFIRMADA
 * @param accionesPorActor       acciones registradas por audit, por usuario (audit.timeline)
 * @param ultimasAcciones        últimas entradas de audit.timeline, la más reciente primero
 * @param duplicadosDescartados  mensajes repetidos ignorados (idempotencia por eventId)
 */
public record RealtimeKpiResponse(
        Instant generadoEn,
        long totalEventos,
        Map<String, Long> eventosPorTipo,
        Map<String, Long> reservasPorEstado,
        Map<String, Long> reservasCreadasPorHora,
        List<TopAlojamiento> topAlojamientos,
        BigDecimal ingresosConfirmados,
        Map<String, Long> accionesPorActor,
        List<TimelineEntryMessage> ultimasAcciones,
        Instant ultimoEvento,
        long duplicadosDescartados) {

    public record TopAlojamiento(String alojamiento, long reservas) {
    }
}
