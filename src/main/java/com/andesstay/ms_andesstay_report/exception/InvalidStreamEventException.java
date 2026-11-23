package com.andesstay.ms_andesstay_report.exception;

/**
 * Mensaje Kafka que no se puede procesar (payload vacío o sin datos obligatorios).
 * No se reintenta: va directo al DLT.
 */
public class InvalidStreamEventException extends RuntimeException {

    public InvalidStreamEventException(String message) {
        super(message);
    }
}
