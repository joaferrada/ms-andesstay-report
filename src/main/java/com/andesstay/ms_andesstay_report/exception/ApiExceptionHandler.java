package com.andesstay.ms_andesstay_report.exception;

import com.andesstay.ms_andesstay_report.dto.KpiRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce excepciones a respuestas JSON {timestamp, status, error, message}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        String mensaje = "range".equals(ex.getName())
                ? "range invalido: " + ex.getValue() + ". Valores validos: " + String.join(", ", KpiRange.codes())
                : "Parametro invalido: " + ex.getName();
        return error(HttpStatus.BAD_REQUEST, mensaje);
    }

    /** @Min/@Max de limit. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> validacion(HandlerMethodValidationException ex) {
        String detalle = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> r.getMethodParameter().getParameterName() + ": " + e.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> servicioNoDisponible(ServiceUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /** 4xx de reservations (ej. 401 token vencido): mismo codigo y cuerpo. */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<String> errorDelMicroservicio(HttpClientErrorException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.getResponseBodyAsString());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}
