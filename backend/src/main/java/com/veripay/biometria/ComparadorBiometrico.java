package com.veripay.biometria;

import java.math.BigDecimal;

/**
 * Compara la foto del documento de identidad contra la selfie del cliente.
 * Implementaciones: {@link ComparadorFacial} (reconocimiento facial con OpenCV, por defecto) y
 * {@link ComparadorPerceptual} (hashes de imagen, para pruebas). Se elige con veripay.biometria.motor.
 */
public interface ComparadorBiometrico {

    /** @return puntaje de similitud; su escala depende del motor (ver {@link #umbralRecomendado()}) */
    BigDecimal comparar(byte[] imagenIdentificacion, byte[] imagenSelfie);

    /** Puntaje mínimo para considerar que ambas imágenes son de la misma persona. */
    BigDecimal umbralRecomendado();
}
