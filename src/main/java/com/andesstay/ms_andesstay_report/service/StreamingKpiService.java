package com.andesstay.ms_andesstay_report.service;

import com.andesstay.ms_andesstay_report.dto.RealtimeKpiResponse;
import com.andesstay.ms_andesstay_report.exception.InvalidStreamEventException;
import com.andesstay.ms_andesstay_report.kafka.ReservationEventMessage;
import com.andesstay.ms_andesstay_report.kafka.TimelineEntryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Proyección en memoria de los eventos de streaming: cada evento actualiza los contadores al
 * llegar, sin consultar a otros microservicios. No conoce Kafka (recibe DTO y lanza excepciones).
 *
 * Los métodos son synchronized: los listeners corren en varios hilos (uno por partición).
 */
@Service
public class StreamingKpiService {

    private static final Logger log = LoggerFactory.getLogger(StreamingKpiService.class);
    static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00").withZone(ZONA);
    static final int HORAS_REPORTADAS = 24;
    static final int TOP_ALOJAMIENTOS = 5;
    static final int ULTIMAS_ACCIONES = 10;

    private final Clock clock;

    private final Set<String> eventosProcesados = new HashSet<>();
    private final Set<Long> timelineProcesado = new HashSet<>();
    private final Map<String, Long> eventosPorTipo = new TreeMap<>();
    private final Map<Long, String> estadoActual = new HashMap<>();
    private final Map<String, Long> creadasPorHora = new TreeMap<>();
    private final Map<String, Long> reservasPorAlojamiento = new HashMap<>();
    private final Map<String, Long> accionesPorActor = new TreeMap<>();
    private final Deque<TimelineEntryMessage> ultimasAcciones = new ArrayDeque<>();
    private BigDecimal ingresosConfirmados = BigDecimal.ZERO;
    private Instant ultimoEvento;
    private long totalEventos;
    private long duplicados;

    public StreamingKpiService() {
        this(Clock.systemUTC());
    }

    StreamingKpiService(Clock clock) {
        this.clock = clock;
    }

    /** @throws InvalidStreamEventException si el evento no trae los datos mínimos */
    public synchronized void onReservationEvent(ReservationEventMessage event) {
        if (event == null || event.eventId() == null || event.reservationId() == null || event.eventType() == null) {
            throw new InvalidStreamEventException("evento de reserva incompleto: " + event);
        }
        if (!eventosProcesados.add(event.eventId())) {
            duplicados++;
            log.debug("[KPI] duplicado ignorado eventId={}", event.eventId());
            return;
        }

        totalEventos++;
        eventosPorTipo.merge(event.eventType(), 1L, Long::sum);
        Instant cuando = event.occurredAt() == null ? Instant.now(clock) : event.occurredAt();
        if (ultimoEvento == null || cuando.isAfter(ultimoEvento)) {
            ultimoEvento = cuando;
        }

        switch (event.eventType()) {
            case "RESERVATION_CREATED" -> {
                creadasPorHora.merge(HORA.format(cuando), 1L, Long::sum);
                if (event.alojamientoNombre() != null) {
                    reservasPorAlojamiento.merge(event.alojamientoNombre(), 1L, Long::sum);
                }
                actualizarEstado(event);
            }
            case "RESERVATION_STATUS_CHANGED" -> {
                if ("CONFIRMADA".equals(event.newStatus()) && event.precioTotal() != null) {
                    ingresosConfirmados = ingresosConfirmados.add(event.precioTotal());
                }
                actualizarEstado(event);
            }
            case "RESERVATION_DELETED" -> estadoActual.remove(event.reservationId());
            default -> actualizarEstado(event);
        }
        log.info("[KPI] eventId={} type={} reservationId={} totalEventos={}",
                event.eventId(), event.eventType(), event.reservationId(), totalEventos);
    }

    /** @throws InvalidStreamEventException si la entrada no trae los datos mínimos */
    public synchronized void onTimelineEntry(TimelineEntryMessage entry) {
        if (entry == null || entry.auditId() == null || entry.reservationId() == null) {
            throw new InvalidStreamEventException("entrada de timeline incompleta: " + entry);
        }
        if (!timelineProcesado.add(entry.auditId())) {
            duplicados++;
            return;
        }
        accionesPorActor.merge(entry.actor() == null ? "desconocido" : entry.actor(), 1L, Long::sum);
        ultimasAcciones.addFirst(entry);
        while (ultimasAcciones.size() > ULTIMAS_ACCIONES) {
            ultimasAcciones.removeLast();
        }
    }

    public synchronized RealtimeKpiResponse snapshot() {
        Map<String, Long> porEstado = new TreeMap<>();
        estadoActual.values().forEach(estado -> porEstado.merge(estado, 1L, Long::sum));

        Map<String, Long> ultimasHoras = new LinkedHashMap<>();
        creadasPorHora.entrySet().stream()
                .skip(Math.max(0, creadasPorHora.size() - HORAS_REPORTADAS))
                .forEach(e -> ultimasHoras.put(e.getKey(), e.getValue()));

        List<RealtimeKpiResponse.TopAlojamiento> top = reservasPorAlojamiento.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_ALOJAMIENTOS)
                .map(e -> new RealtimeKpiResponse.TopAlojamiento(e.getKey(), e.getValue()))
                .toList();

        return new RealtimeKpiResponse(
                Instant.now(clock),
                totalEventos,
                new TreeMap<>(eventosPorTipo),
                porEstado,
                ultimasHoras,
                top,
                ingresosConfirmados,
                new TreeMap<>(accionesPorActor),
                List.copyOf(ultimasAcciones),
                ultimoEvento,
                duplicados);
    }

    private void actualizarEstado(ReservationEventMessage event) {
        if (event.newStatus() != null) {
            estadoActual.put(event.reservationId(), event.newStatus());
        }
    }
}
