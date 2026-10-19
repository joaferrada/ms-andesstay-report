package com.andesstay.ms_andesstay_report.service;

import com.andesstay.ms_andesstay_report.client.ReservationView;
import com.andesstay.ms_andesstay_report.client.TimelineEvent;
import com.andesstay.ms_andesstay_report.dto.TopUnit;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Calculos puros de los KPIs: reciben los datos ya obtenidos y no llaman a ningun servicio.
 */
@Component
public class KpiCalculator {

    static final String EN_ESTADIA = "EN_ESTADIA";
    static final String CHECKOUT = "CHECKOUT";
    /** Estados que cuentan como ingreso: desde CONFIRMADA en adelante, sin CANCELADA. */
    static final Set<String> ESTADOS_CON_INGRESO = Set.of("CONFIRMADA", "CHECKIN_PENDIENTE", EN_ESTADIA, CHECKOUT);

    private static final String RESERVA_CREADA = "RESERVA_CREADA";
    private static final String CAMBIO_ESTADO = "CAMBIO_ESTADO";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");

    /** Reservas con creadaEn en [desde, hasta]. */
    public List<ReservationView> enRango(Collection<ReservationView> reservas, LocalDateTime desde, LocalDateTime hasta) {
        return reservas.stream()
                .filter(r -> r.creadaEn() != null && !r.creadaEn().isBefore(desde) && !r.creadaEn().isAfter(hasta))
                .toList();
    }

    /** Cantidad por hora de creacion, ordenado cronologicamente. */
    public Map<String, Long> reservasPorHora(Collection<ReservationView> reservas) {
        return reservas.stream()
                .filter(r -> r.creadaEn() != null)
                .collect(Collectors.groupingBy(r -> r.creadaEn().truncatedTo(ChronoUnit.HOURS).format(HORA),
                        TreeMap::new, Collectors.counting()));
    }

    public Map<String, Long> reservasPorEstado(Collection<ReservationView> reservas) {
        return reservas.stream()
                .filter(r -> r.estado() != null)
                .collect(Collectors.groupingBy(ReservationView::estado, TreeMap::new, Collectors.counting()));
    }

    public long ocupacionActiva(Collection<ReservationView> reservas) {
        return reservas.stream().filter(r -> EN_ESTADIA.equals(r.estado())).count();
    }

    public double ingresosConfirmados(Collection<ReservationView> reservas) {
        return reservas.stream()
                .filter(r -> ESTADOS_CON_INGRESO.contains(r.estado()))
                .map(ReservationView::precioTotal)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    /**
     * Promedio de horas entre RESERVA_CREADA y el CAMBIO_ESTADO a CHECKOUT, redondeado a 2 decimales.
     * Solo cuentan los timelines que tienen ambos eventos.
     *
     * @return vacio si ningun timeline tiene el ciclo completo
     */
    public Optional<Double> tiempoCicloPromedioHoras(Collection<List<TimelineEvent>> timelines) {
        return timelines.stream()
                .map(KpiCalculator::horasDeCiclo)
                .flatMap(Optional::stream)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream().boxed()
                .map(h -> Math.round(h * 100.0) / 100.0)
                .findFirst();
    }

    /** Unidades con mas reservas (desempate por unidadId). Las reservas sin unidadId no cuentan. */
    public List<TopUnit> topUnits(Collection<ReservationView> reservas, int limit) {
        Map<Long, List<ReservationView>> porUnidad = reservas.stream()
                .filter(r -> r.unidadId() != null)
                .collect(Collectors.groupingBy(ReservationView::unidadId));
        return porUnidad.entrySet().stream()
                .map(e -> new TopUnit(e.getKey(), nombre(e.getValue()), e.getValue().size()))
                .sorted(Comparator.comparingLong(TopUnit::reservas).reversed().thenComparing(TopUnit::unidadId))
                .limit(limit)
                .toList();
    }

    private static Optional<Double> horasDeCiclo(List<TimelineEvent> timeline) {
        Optional<Instant> creada = primero(timeline, e -> RESERVA_CREADA.equals(e.tipo()));
        Optional<Instant> checkout = primero(timeline,
                e -> CAMBIO_ESTADO.equals(e.tipo()) && CHECKOUT.equals(e.estadoNuevo()));
        if (creada.isEmpty() || checkout.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(creada.get(), checkout.get()).toSeconds() / 3600.0);
    }

    private static Optional<Instant> primero(List<TimelineEvent> timeline,
                                             Predicate<TimelineEvent> condicion) {
        return timeline.stream()
                .filter(condicion)
                .map(TimelineEvent::occurredAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());
    }

    private static String nombre(List<ReservationView> reservas) {
        return reservas.stream().map(ReservationView::alojamientoNombre).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
}
