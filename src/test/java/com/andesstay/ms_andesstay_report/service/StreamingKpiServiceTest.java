package com.andesstay.ms_andesstay_report.service;

import com.andesstay.ms_andesstay_report.dto.RealtimeKpiResponse;
import com.andesstay.ms_andesstay_report.exception.InvalidStreamEventException;
import com.andesstay.ms_andesstay_report.kafka.ReservationEventMessage;
import com.andesstay.ms_andesstay_report.kafka.TimelineEntryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamingKpiServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-23T13:15:00Z"); // 10:15 en Santiago

    private StreamingKpiService service;

    @BeforeEach
    void setUp() {
        service = new StreamingKpiService(Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void creacionYConfirmacionActualizanLosKpis() {
        service.onReservationEvent(evento("e-1", "RESERVATION_CREATED", 1L, null, "CREADA", "Cabana Lago"));
        service.onReservationEvent(evento("e-2", "RESERVATION_CREATED", 2L, null, "CREADA", "Cabana Lago"));
        service.onReservationEvent(evento("e-3", "RESERVATION_STATUS_CHANGED", 1L, "CREADA", "CONFIRMADA", "Cabana Lago"));

        RealtimeKpiResponse kpis = service.snapshot();

        assertThat(kpis.totalEventos()).isEqualTo(3);
        assertThat(kpis.eventosPorTipo()).containsEntry("RESERVATION_CREATED", 2L)
                .containsEntry("RESERVATION_STATUS_CHANGED", 1L);
        assertThat(kpis.reservasPorEstado()).containsEntry("CREADA", 1L).containsEntry("CONFIRMADA", 1L);
        assertThat(kpis.reservasCreadasPorHora()).containsEntry("2026-11-23 10:00", 2L);
        assertThat(kpis.topAlojamientos()).first()
                .isEqualTo(new RealtimeKpiResponse.TopAlojamiento("Cabana Lago", 2));
        assertThat(kpis.ingresosConfirmados()).isEqualByComparingTo("180000");
        assertThat(kpis.ultimoEvento()).isEqualTo(T0);
    }

    @Test
    void eventoRepetidoNoSeCuentaDosVeces() {
        ReservationEventMessage evento = evento("e-1", "RESERVATION_CREATED", 1L, null, "CREADA", "Cabana Lago");

        service.onReservationEvent(evento);
        service.onReservationEvent(evento);

        assertThat(service.snapshot().totalEventos()).isEqualTo(1);
        assertThat(service.snapshot().duplicadosDescartados()).isEqualTo(1);
    }

    @Test
    void reservaEliminadaSaleDelConteoPorEstado() {
        service.onReservationEvent(evento("e-1", "RESERVATION_CREATED", 1L, null, "CREADA", "Cabana Lago"));
        service.onReservationEvent(evento("e-2", "RESERVATION_DELETED", 1L, "CREADA", "CREADA", "Cabana Lago"));

        assertThat(service.snapshot().reservasPorEstado()).isEmpty();
    }

    @Test
    void timelineCuentaAccionesPorActor() {
        service.onTimelineEntry(entrada(1L, "recepcion@andesstay.cl"));
        service.onTimelineEntry(entrada(2L, "recepcion@andesstay.cl"));
        service.onTimelineEntry(entrada(2L, "recepcion@andesstay.cl")); // duplicado

        RealtimeKpiResponse kpis = service.snapshot();
        assertThat(kpis.accionesPorActor()).containsEntry("recepcion@andesstay.cl", 2L);
        assertThat(kpis.ultimasAcciones()).hasSize(2).first().extracting(TimelineEntryMessage::auditId).isEqualTo(2L);
    }

    @Test
    void eventoIncompletoEsInvalido() {
        assertThatThrownBy(() -> service.onReservationEvent(null)).isInstanceOf(InvalidStreamEventException.class);
        assertThatThrownBy(() -> service.onTimelineEntry(entrada(null, "x"))).isInstanceOf(InvalidStreamEventException.class);
    }

    private static ReservationEventMessage evento(String id, String type, Long reservationId, String anterior,
                                                  String nuevo, String alojamiento) {
        return new ReservationEventMessage(id, type, T0, reservationId, anterior, nuevo, "op@andesstay.cl",
                alojamiento, 10L, null, null, new BigDecimal("180000"), "ms-andesstay-reservations");
    }

    private static TimelineEntryMessage entrada(Long auditId, String actor) {
        return new TimelineEntryMessage(auditId, "e-" + auditId, 1L, "CAMBIO_ESTADO", actor, "CREADA", "CONFIRMADA",
                null, T0, T0, "ms-andesstay-audit");
    }
}
