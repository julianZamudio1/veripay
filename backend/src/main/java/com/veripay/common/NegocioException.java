package com.veripay.common;

import org.springframework.http.HttpStatus;

/** Violación de una regla de negocio (saldo insuficiente, KYC pendiente, etc.). Se responde con 422. */
public class NegocioException extends RuntimeException {

    private final String codigo;
    private final HttpStatus status;

    public NegocioException(String codigo, String mensaje) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensaje);
    }

    protected NegocioException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
        this.status = status;
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
