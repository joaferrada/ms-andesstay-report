package com.andesstay.ms_andesstay_report.service;

import com.andesstay.ms_andesstay_report.client.AuditClient;
import com.andesstay.ms_andesstay_report.client.ReservationView;
import com.andesstay.ms_andesstay_report.client.ReservationsClient;
import com.andesstay.ms_andesstay_report.client.TimelineEvent;
import com.andesstay.ms_andesstay_report.dto.KpiRange;
import com.andesstay.ms_andesstay_report.dto.KpiResponse;
import com.andesstay.ms_andesstay_report.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.andesstay.ms_andesstay_report.service.KpiCalculatorTest.reserva;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Orquestacion con clientes simulados: rango, servicio caido y avisos.
 */
class ReportServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);

    private ReservationsClient reservationsClient;
    private AuditClient auditClient;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reservationsClient = mock(ReservationsClient.class);
        auditClient = mock(AuditClient.class);
        service = new ReportService(reservationsClient, auditClient, new KpiCalculator(), RELOJ);
        when(reservationsClient.listar()).thenReturn(List.of(
                reserva(1L, 10L, "Cabana Lago", "CHECKOUT", 300000.0, AHORA.minusHours(30)),
                reserva(2L, 10L, "Cabana Lago", "EN_ESTADIA", 200000.0, AHORA.minusHours(2)),
                reserva(3L, 20L, "Domo Elqui", "CREADA", 100000.0, AHORA.minusHours(1)),
                // fuera de last24h y de last7d, pero cuenta para ocupacionActiva
                reserva(4L, 20L, "Domo Elqui", "EN_ESTADIA", 150000.0, AHORA.minusDays(10))));
    }

    @Test
    void kpisDelRangoConTiempoDeCicloDesdeAudit() {
        Instant creada = Instant.parse("2026-10-18T09:00:00Z");
        when(auditClient.timeline(1L)).thenReturn(List.of(
                new TimelineEvent("RESERVA_CREADA", null, creada),
                new TimelineEvent("CAMBIO_ESTADO", "CHECKOUT", creada.plusSeconds(12 * 3600))));

        KpiResponse kpis = service.kpis(KpiRange.LAST_7D);

        assertThat(kpis.totalReservas()).isEqualTo(3);
        assertThat(kpis.ocupacionActiva()).isEqualTo(2);
        assertThat(kpis.ingresosConfirmados()).isEqualTo(500000.0);
        assertThat(kpis.tiempoCicloPromedioHoras()).isEqualTo(12.0);
        assertThat(kpis.avisos()).isEmpty();
        assertThat(kpis.desde()).isEqualTo(AHORA.minusDays(7));
        verify(auditClient, only()).timeline(1L);
    }

    @Test
    void last24hDejaFueraLasReservasMasAntiguas() {
        KpiResponse kpis = service.kpis(KpiRange.LAST_24H);

        assertThat(kpis.totalReservas()).isEqualTo(2);
        assertThat(kpis.reservasPorEstado()).containsOnlyKeys("EN_ESTADIA", "CREADA");
        assertThat(kpis.tiempoCicloPromedioHoras()).isNull();
        assertThat(kpis.avisos()).singleElement().asString().contains("no hay reservas con ciclo completo");
        verifyNoInteractions(auditClient);
    }

    @Test
    void auditCaidoDejaSoloElTiempoDeCicloEnNullConAviso() {
        when(auditClient.timeline(anyLong())).thenThrow(new ResourceAccessException("Connection refused"));

        KpiResponse kpis = service.kpis(KpiRange.LAST_7D);

        assertThat(kpis.tiempoCicloPromedioHoras()).isNull();
        assertThat(kpis.avisos()).containsExactly("tiempoCicloPromedioHoras no calculado: ms-andesstay-audit no responde");
        assertThat(kpis.totalReservas()).isEqualTo(3);
        assertThat(kpis.ingresosConfirmados()).isEqualTo(500000.0);
    }

    @Test
    void reservationsCaidoLanza503() {
        when(reservationsClient.listar()).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service.kpis(KpiRange.LAST_7D))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("ms-andesstay-reservations no responde");
        assertThatThrownBy(() -> service.topUnits(KpiRange.LAST_7D, 5))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void errorDeClienteDeReservationsSePropaga() {
        when(reservationsClient.listar()).thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                "Unauthorized", null, null, null));

        assertThatThrownBy(() -> service.kpis(KpiRange.LAST_7D)).isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void topUnitsDelRango() {
        assertThat(service.topUnits(KpiRange.LAST_7D, 1).unidades())
                .singleElement()
                .satisfies(u -> {
                    assertThat(u.unidadId()).isEqualTo(10L);
                    assertThat(u.reservas()).isEqualTo(2);
                });
    }
}
