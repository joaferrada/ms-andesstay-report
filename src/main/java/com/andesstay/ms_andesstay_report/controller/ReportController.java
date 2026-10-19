package com.andesstay.ms_andesstay_report.controller;

import com.andesstay.ms_andesstay_report.dto.KpiRange;
import com.andesstay.ms_andesstay_report.dto.KpiResponse;
import com.andesstay.ms_andesstay_report.dto.TopUnitsResponse;
import com.andesstay.ms_andesstay_report.service.ReportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // KPIs calculados al momento. range: last24h | last7d | last30d (otro valor -> 400)
    @GetMapping("/kpis")
    @PreAuthorize("hasRole('Admin')")
    public KpiResponse kpis(@RequestParam(defaultValue = "last7d") KpiRange range) {
        return reportService.kpis(range);
    }

    // Unidades con mas reservas en el rango
    @GetMapping("/top-units")
    @PreAuthorize("hasRole('Admin')")
    public TopUnitsResponse topUnits(@RequestParam(defaultValue = "last7d") KpiRange range,
                                     @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        return reportService.topUnits(range, limit);
    }
}
