package com.andesstay.ms_andesstay_report.controller;

import com.andesstay.ms_andesstay_report.dto.RealtimeKpiResponse;
import com.andesstay.ms_andesstay_report.service.StreamingKpiService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EP5: KPIs en tiempo real calculados desde Kafka (reservations.events + audit.timeline).
 */
@RestController
@RequestMapping("/api/report")
public class StreamingReportController {

    private final StreamingKpiService kpiService;

    public StreamingReportController(StreamingKpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/realtime")
    @PreAuthorize("hasRole('Admin')")
    public RealtimeKpiResponse realtime() {
        return kpiService.snapshot();
    }
}
