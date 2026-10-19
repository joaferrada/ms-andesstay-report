package com.andesstay.ms_andesstay_report.exception;

/**
 * Un microservicio del que depende el reporte no responde. Se responde 503.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
