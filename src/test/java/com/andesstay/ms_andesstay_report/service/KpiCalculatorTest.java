package com.andesstay.ms_andesstay_report.service;

import com.andesstay.ms_andesstay_report.client.ReservationView;
import com.andesstay.ms_andesstay_report.client.TimelineEvent;
import com.andesstay.ms_andesstay_report.dto.TopUnit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calculos con datos de prueba (sin llamadas a otros servicios).
 */
class KpiCalculatorTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 18, 10, 0);

    private final KpiCalculator calculator = new KpiCalculator();

    private final List<ReservationView> reservas = List.of(
            reserva(1L, 10L, "Cabana Lago", "CREADA", 100000.0, BASE.plusMinutes(5)),
            reserva(2L, 10L, "Cabana Lago", "CONFIRMADA", 200000.0, BASE.plusMinutes(40)),
            reserva(3L, 20L, "Domo Elqui", "EN_ESTADIA", 150000.0, BASE.plusHours(1)),
            reserva(4L, 10L, "Cabana Lago", "CHECKOUT", 300000.0, BASE.plusHours(2)),
            reserva(5L, 30L, "Lodge Osorno", "CANCELADA", 999999.0, BASE.plusHours(2)),
            reserva(6L, null, "Sin unidad", "CHECKIN_PENDIENTE", 50000.0, BASE.plusHours(3)));

    @Test
    void enRangoFiltraPorCreadaEnInclusive() {
        assertThat(calculator.enRango(reservas, BASE.plusHours(1), BASE.plusHours(2)))
                .extracting(ReservationView::id).containsExactly(3L, 4L, 5L);
    }

    @Test
    void reservasPorHoraAgrupaYOrdena() {
        assertThat(calculator.reservasPorHora(reservas)).containsExactly(
                Map.entry("2026-10-18T10:00", 2L),
                Map.entry("2026-10-18T11:00", 1L),
                Map.entry("2026-10-18T12:00", 2L),
                Map.entry("2026-10-18T13:00", 1L));
    }

    @Test
    void reservasPorEstadoYOcupacionActiva() {
        assertThat(calculator.reservasPorEstado(reservas))
                .containsEntry("CONFIRMADA", 1L)
                .containsEntry("CANCELADA", 1L)
                .hasSize(6);
        assertThat(calculator.ocupacionActiva(reservas)).isEqualTo(1);
    }

    @Test
    void ingresosDesdeConfirmadaSinCanceladasNiCreadas() {
        // CONFIRMADA 200000 + EN_ESTADIA 150000 + CHECKOUT 300000 + CHECKIN_PENDIENTE 50000
        assertThat(calculator.ingresosConfirmados(reservas)).isEqualTo(700000.0);
    }

    @Test
    void tiempoCicloPromedioSoloConCiclosCompletos() {
        Instant t0 = Instant.parse("2026-10-01T12:00:00Z");
        List<TimelineEvent> ciclo48h = List.of(
                new TimelineEvent("RESERVA_CREADA", null, t0),
                new TimelineEvent("CAMBIO_ESTADO", "CONFIRMADA", t0.plusSeconds(3600)),
                new TimelineEvent("CAMBIO_ESTADO", "CHECKOUT", t0.plusSeconds(48 * 3600)));
        List<TimelineEvent> ciclo24h30m = List.of(
                new TimelineEvent("RESERVA_CREADA", null, t0),
                new TimelineEvent("CAMBIO_ESTADO", "CHECKOUT", t0.plusSeconds(24 * 3600 + 1800)));
        List<TimelineEvent> sinCreacion = List.of(new TimelineEvent("CAMBIO_ESTADO", "CHECKOUT", t0));

        assertThat(calculator.tiempoCicloPromedioHoras(List.of(ciclo48h, ciclo24h30m, sinCreacion)))
                .contains(36.25);
        assertThat(calculator.tiempoCicloPromedioHoras(List.of(sinCreacion))).isEmpty();
        assertThat(calculator.tiempoCicloPromedioHoras(List.of())).isEmpty();
    }

    @Test
    void topUnitsOrdenaPorCantidadYRespetaElLimite() {
        assertThat(calculator.topUnits(reservas, 2)).containsExactly(
                new TopUnit(10L, "Cabana Lago", 3),
                new TopUnit(20L, "Domo Elqui", 1));
        assertThat(calculator.topUnits(reservas, 10)).extracting(TopUnit::unidadId)
                .containsExactly(10L, 20L, 30L);
    }

    static ReservationView reserva(Long id, Long unidadId, String nombre, String estado, Double precio,
                                   LocalDateTime creadaEn) {
        return new ReservationView(id, unidadId, nombre, estado, precio, creadaEn);
    }
}
