package com.veripay.common;

/** Violación de una regla de negocio (saldo insuficiente, KYC pendiente, etc.). Se responde con 422. */
public class NegocioException extends RuntimeException {

    private final String codigo;

    public NegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
