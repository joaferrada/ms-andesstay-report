package com.andesstay.ms_andesstay_report.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta de GET /api/report/top-units (solo reservas con unidadId).
 */
public record TopUnitsResponse(
        KpiRange range,
        LocalDateTime desde,
        LocalDateTime hasta,
        int limit,
        List<TopUnit> unidades) {
}
