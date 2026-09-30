package com.veripay.common;

import org.springframework.http.HttpStatus;

/** El recurso ya existe o su estado actual impide la operación. Se responde con 409. */
public class ConflictoException extends NegocioException {

    public ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
