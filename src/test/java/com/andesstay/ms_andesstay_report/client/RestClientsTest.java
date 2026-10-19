package com.andesstay.ms_andesstay_report.client;

import com.andesstay.ms_andesstay_report.config.RestClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Los clientes usan la URL configurada y propagan el JWT del usuario (sin llamadas reales).
 */
class RestClientsTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reservationsPropagaElJwtYLeeLasReservas() {
        autenticar("token-admin");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReservationsClient client = new ReservationsClient(RestClientConfig.conJwt(builder, "http://reservations:8081"));

        server.expect(requestTo("http://reservations:8081/api/reservations"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-admin"))
                .andRespond(withSuccess("""
                        [{"id":1,"unidadId":10,"alojamientoNombre":"Cabana Lago","estado":"CONFIRMADA",
                          "precioTotal":250000.0,"creadaEn":"2026-10-18T10:15:30.123","huespedEmail":"ana@andesstay.cl"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listar()).containsExactly(new ReservationView(1L, 10L, "Cabana Lago", "CONFIRMADA",
                250000.0, LocalDateTime.of(2026, 10, 18, 10, 15, 30, 123_000_000)));
        server.verify();
    }

    @Test
    void auditPideElTimelineDeLaReserva() {
        autenticar("token-admin");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AuditClient client = new AuditClient(RestClientConfig.conJwt(builder, "http://audit:8086"));

        server.expect(requestTo("http://audit:8086/api/audit/reservations/7/timeline"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-admin"))
                .andRespond(withSuccess("""
                        [{"id":1,"tipo":"RESERVA_CREADA","reservationId":7,"actor":"ana@andesstay.cl",
                          "occurredAt":"2026-10-18T10:00:00Z"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.timeline(7L)).singleElement()
                .extracting(TimelineEvent::tipo).isEqualTo("RESERVA_CREADA");
        server.verify();
    }

    private static void autenticar(String token) {
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "none").subject("admin@andesstay.cl").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
