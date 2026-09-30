package com.veripay.biometria;

import java.math.BigDecimal;

/**
 * Compara la foto del documento de identidad contra la selfie del cliente.
 * La implementación incluida es perceptual (demo); en producción se sustituye por un
 * proveedor de reconocimiento facial con prueba de vida sin tocar el resto del sistema.
 */
public interface ComparadorBiometrico {

    /** @return puntaje de similitud entre 0 y 1 */
    BigDecimal comparar(byte[] imagenIdentificacion, byte[] imagenSelfie);
}
