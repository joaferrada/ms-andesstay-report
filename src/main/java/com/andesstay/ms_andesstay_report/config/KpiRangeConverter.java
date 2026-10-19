package com.andesstay.ms_andesstay_report.config;

import com.andesstay.ms_andesstay_report.dto.KpiRange;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * ?range=last7d -> KpiRange.LAST_7D. Un valor desconocido termina en 400 (ApiExceptionHandler).
 */
@Component
public class KpiRangeConverter implements Converter<String, KpiRange> {

    @Override
    public KpiRange convert(String source) {
        return KpiRange.fromCode(source.trim())
                .orElseThrow(() -> new IllegalArgumentException("range invalido: " + source));
    }
}
