package com.andesstay.ms_andesstay_report.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * KPIs de GET /api/report/kpis.
 *
 * @param reservasPorHora           reservas creadas en el rango, por hora (clave yyyy-MM-ddTHH:00)
 * @param reservasPorEstado         reservas creadas en el rango, por estado actual
 * @param ocupacionActiva           reservas en EN_ESTADIA ahora (sin filtro de rango)
 * @param tiempoCicloPromedioHoras  RESERVA_CREADA -> CHECKOUT segun audit; null si no se pudo calcular (ver avisos)
 * @param ingresosConfirmados       suma de precioTotal desde CONFIRMADA en adelante, sin CANCELADA
 * @param avisos                    KPIs que no se pudieron calcular y por que
 */
public record KpiResponse(
        KpiRange range,
        LocalDateTime desde,
        LocalDateTime hasta,
        long totalReservas,
        Map<String, Long> reservasPorHora,
        Map<String, Long> reservasPorEstado,
        long ocupacionActiva,
        Double tiempoCicloPromedioHoras,
        double ingresosConfirmados,
        List<String> avisos) {
}
