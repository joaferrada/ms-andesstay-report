package com.andesstay.ms_andesstay_report.service;

import com.andesstay.ms_andesstay_report.client.AuditClient;
import com.andesstay.ms_andesstay_report.client.ReservationView;
import com.andesstay.ms_andesstay_report.client.ReservationsClient;
import com.andesstay.ms_andesstay_report.client.TimelineEvent;
import com.andesstay.ms_andesstay_report.dto.KpiRange;
import com.andesstay.ms_andesstay_report.dto.KpiResponse;
import com.andesstay.ms_andesstay_report.dto.TopUnitsResponse;
import com.andesstay.ms_andesstay_report.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orquesta las consultas a reservations y audit y delega los calculos en {@link KpiCalculator}.
 *
 * - reservations no responde: 503 (sin reservas no hay ningun KPI).
 * - audit no responde: solo tiempoCicloPromedioHoras queda null, con un aviso.
 * - 4xx de reservations (ej. 401 por token vencido): se propaga tal cual.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReservationsClient reservationsClient;
    private final AuditClient auditClient;
    private final KpiCalculator calculator;
    private final Clock clock;

    public ReportService(ReservationsClient reservationsClient, AuditClient auditClient,
                         KpiCalculator calculator, Clock clock) {
        this.reservationsClient = reservationsClient;
        this.auditClient = auditClient;
        this.calculator = calculator;
        this.clock = clock;
    }

    public KpiResponse kpis(KpiRange range) {
        LocalDateTime hasta = LocalDateTime.now(clock);
        LocalDateTime desde = hasta.minus(range.duration());
        List<ReservationView> todas = obtenerReservas();
        List<ReservationView> enRango = calculator.enRango(todas, desde, hasta);

        List<String> avisos = new ArrayList<>();
        Double tiempoCiclo = tiempoCiclo(enRango, avisos);

        return new KpiResponse(range, desde, hasta, enRango.size(),
                calculator.reservasPorHora(enRango),
                calculator.reservasPorEstado(enRango),
                calculator.ocupacionActiva(todas),
                tiempoCiclo,
                calculator.ingresosConfirmados(enRango),
                avisos);
    }

    public TopUnitsResponse topUnits(KpiRange range, int limit) {
        LocalDateTime hasta = LocalDateTime.now(clock);
        LocalDateTime desde = hasta.minus(range.duration());
        List<ReservationView> enRango = calculator.enRango(obtenerReservas(), desde, hasta);
        return new TopUnitsResponse(range, desde, hasta, limit, calculator.topUnits(enRango, limit));
    }

    private List<ReservationView> obtenerReservas() {
        try {
            return reservationsClient.listar();
        } catch (HttpClientErrorException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("[REPORT] ms-andesstay-reservations no responde: {}", ex.getMessage());
            throw new ServiceUnavailableException(
                    "ms-andesstay-reservations no responde; no se pueden calcular los reportes. Intente mas tarde", ex);
        }
    }

    /** Timeline de cada reserva del rango en CHECKOUT. Si audit falla: null + aviso. */
    private Double tiempoCiclo(List<ReservationView> enRango, List<String> avisos) {
        List<ReservationView> conCheckout = enRango.stream()
                .filter(r -> KpiCalculator.CHECKOUT.equals(r.estado()) && r.id() != null)
                .toList();
        List<List<TimelineEvent>> timelines = new ArrayList<>();
        try {
            for (ReservationView r : conCheckout) {
                timelines.add(auditClient.timeline(r.id()));
            }
        } catch (RestClientException ex) {
            log.warn("[REPORT] ms-andesstay-audit no responde: {}", ex.getMessage());
            avisos.add("tiempoCicloPromedioHoras no calculado: ms-andesstay-audit no responde");
            return null;
        }
        Optional<Double> promedio = calculator.tiempoCicloPromedioHoras(timelines);
        if (promedio.isEmpty()) {
            avisos.add("tiempoCicloPromedioHoras no calculado: no hay reservas con ciclo completo"
                    + " (RESERVA_CREADA y CHECKOUT) en el rango");
        }
        return promedio.orElse(null);
    }
}
