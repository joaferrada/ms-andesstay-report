package com.andesstay.ms_andesstay_report.controller;

import com.andesstay.ms_andesstay_report.config.KpiRangeConverter;
import com.andesstay.ms_andesstay_report.dto.KpiRange;
import com.andesstay.ms_andesstay_report.dto.KpiResponse;
import com.andesstay.ms_andesstay_report.dto.TopUnitsResponse;
import com.andesstay.ms_andesstay_report.exception.ApiExceptionHandler;
import com.andesstay.ms_andesstay_report.exception.ServiceUnavailableException;
import com.andesstay.ms_andesstay_report.security.JwtAuthConverter;
import com.andesstay.ms_andesstay_report.security.SecurityConfig;
import com.andesstay.ms_andesstay_report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Solo Admin; range invalido -> 400; reservations caido -> 503.
 */
@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class, ApiExceptionHandler.class, KpiRangeConverter.class})
class ReportControllerSecurityTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(reportService.kpis(any())).thenAnswer(inv -> new KpiResponse(inv.getArgument(0), AHORA.minusDays(7), AHORA,
                0, Map.of(), Map.of(), 0, null, 0.0, List.of()));
        when(reportService.topUnits(any(), anyInt())).thenAnswer(inv -> new TopUnitsResponse(inv.getArgument(0),
                AHORA.minusDays(7), AHORA, inv.getArgument(1), List.of()));
    }

    @ParameterizedTest(name = "GET /api/report/kpis como {0} -> {1}")
    @CsvSource({"Admin,200", "Operador_Dominio,403", "Cliente_Dominio,403", "Auditor,403", "Sin_Rol,403"})
    void kpis(String rol, int esperado) throws Exception {
        mockMvc.perform(get("/api/report/kpis").param("range", "last24h").with(usuario(rol)))
                .andExpect(status().is(esperado));
    }

    @ParameterizedTest(name = "GET /api/report/top-units como {0} -> {1}")
    @CsvSource({"Admin,200", "Operador_Dominio,403", "Cliente_Dominio,403", "Auditor,403", "Sin_Rol,403"})
    void topUnits(String rol, int esperado) throws Exception {
        mockMvc.perform(get("/api/report/top-units").with(usuario(rol))).andExpect(status().is(esperado));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/report/kpis")).andExpect(status().isUnauthorized());
    }

    @Test
    void rangeSeConvierteYViajaComoCodigo() throws Exception {
        mockMvc.perform(get("/api/report/kpis").param("range", "last30d").with(usuario("Admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("last30d"));
        verify(reportService).kpis(KpiRange.LAST_30D);
    }

    @Test
    void rangeInvalidoResponde400ConLosValoresValidos() throws Exception {
        mockMvc.perform(get("/api/report/kpis").param("range", "last1y").with(usuario("Admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("range invalido: last1y. Valores validos: last24h, last7d, last30d"));
        verify(reportService, never()).kpis(any());
    }

    @Test
    void limitFueraDeRangoResponde400() throws Exception {
        mockMvc.perform(get("/api/report/top-units").param("limit", "0").with(usuario("Admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(reportService, never()).topUnits(any(), anyInt());
    }

    @Test
    void topUnitsPorDefectoLast7dYLimit5() throws Exception {
        mockMvc.perform(get("/api/report/top-units").with(usuario("Admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("last7d"))
                .andExpect(jsonPath("$.limit").value(5));
    }

    @Test
    void reservationsCaidoResponde503ConMensaje() throws Exception {
        when(reportService.kpis(any())).thenThrow(new ServiceUnavailableException(
                "ms-andesstay-reservations no responde; no se pueden calcular los reportes. Intente mas tarde", null));

        mockMvc.perform(get("/api/report/kpis").with(usuario("Admin")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "ms-andesstay-reservations no responde; no se pueden calcular los reportes. Intente mas tarde"));
    }

    private static RequestPostProcessor usuario(String rol) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }
}
