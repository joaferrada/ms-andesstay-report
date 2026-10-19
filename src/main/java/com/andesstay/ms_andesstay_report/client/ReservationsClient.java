package com.andesstay.ms_andesstay_report.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * GET /api/reservations de ms-andesstay-reservations (con token Admin devuelve todas).
 * Los errores se propagan como RestClientException; ReportService decide como responder.
 */
@Component
public class ReservationsClient {

    private final RestClient restClient;

    public ReservationsClient(@Qualifier("reservationsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ReservationView> listar() {
        List<ReservationView> reservas = restClient.get()
                .uri("/api/reservations")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ReservationView>>() {});
        return reservas != null ? reservas : List.of();
    }
}
