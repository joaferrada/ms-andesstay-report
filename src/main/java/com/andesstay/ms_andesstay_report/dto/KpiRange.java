package com.andesstay.ms_andesstay_report.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Rango de los KPIs (?range=last24h|last7d|last30d), contado hacia atras desde ahora.
 */
public enum KpiRange {
    LAST_24H("last24h", Duration.ofHours(24)),
    LAST_7D("last7d", Duration.ofDays(7)),
    LAST_30D("last30d", Duration.ofDays(30));

    private final String code;
    private final Duration duration;

    KpiRange(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public Duration duration() {
        return duration;
    }

    public static Optional<KpiRange> fromCode(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equalsIgnoreCase(code)).findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(KpiRange::code).toList();
    }
}
