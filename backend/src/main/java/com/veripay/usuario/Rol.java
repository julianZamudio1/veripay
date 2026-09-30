package com.veripay.usuario;

public enum Rol {
    /** Administra usuarios y puede ejecutar cualquier operación. */
    ADMIN,
    /** Da de alta clientes, ejecuta verificaciones KYC y opera cuentas. */
    ANALISTA,
    /** Solo lectura: tableros y auditoría. */
    AUDITOR
}
