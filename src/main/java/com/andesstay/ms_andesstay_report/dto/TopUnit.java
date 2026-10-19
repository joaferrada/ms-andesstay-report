package com.andesstay.ms_andesstay_report.dto;

/**
 * Unidad del catalogo con su cantidad de reservas en el rango.
 */
public record TopUnit(
        Long unidadId,
        String alojamientoNombre,
        long reservas) {
}
