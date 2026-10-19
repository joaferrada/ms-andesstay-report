package com.andesstay.ms_andesstay_report.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * GET /api/audit/reservations/{id}/timeline de ms-andesstay-audit.
 * Los errores se propagan como RestClientException; ReportService decide como responder.
 */
@Component
public class AuditClient {

    private final RestClient restClient;

    public AuditClient(@Qualifier("auditRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<TimelineEvent> timeline(Long reservationId) {
        List<TimelineEvent> eventos = restClient.get()
                .uri("/api/audit/reservations/{id}/timeline", reservationId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TimelineEvent>>() {});
        return eventos != null ? eventos : List.of();
    }
}
