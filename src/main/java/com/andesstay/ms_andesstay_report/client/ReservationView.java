package com.andesstay.ms_andesstay_report.client;

import java.time.LocalDateTime;

/**
 * Campos de una reserva de ms-andesstay-reservations que usan los KPIs (el resto se ignora).
 *
 * @param estado    CREADA, CONFIRMADA, CHECKIN_PENDIENTE, EN_ESTADIA, CHECKOUT o CANCELADA
 * @param creadaEn  hora local de reservations
 */
public record ReservationView(
        Long id,
        Long unidadId,
        String alojamientoNombre,
        String estado,
        Double precioTotal,
        LocalDateTime creadaEn) {
}
