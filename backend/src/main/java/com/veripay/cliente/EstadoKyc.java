package com.veripay.cliente;

/** Estado del proceso Know Your Customer. */
public enum EstadoKyc {
    /** Alta capturada, falta la verificación biométrica. */
    PENDIENTE,
    /** Identidad validada: puede abrir cuentas y operar. */
    VERIFICADO,
    /** La verificación no superó el umbral; puede reintentarse. */
    RECHAZADO
}
