package com.veripay.biometria;

import java.util.Optional;

/** Lee datos impresos en una identificación oficial. */
public interface LectorDocumento {

    /**
     * @return la CURP impresa en la credencial, solo si es una CURP válida (formato, fecha y dígito
     *         verificador); vacío si no se pudo leer con certeza
     */
    Optional<String> leerCurp(byte[] imagenIdentificacion);
}
