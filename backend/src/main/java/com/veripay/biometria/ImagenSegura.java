package com.veripay.biometria;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Valida una imagen leyendo solo su cabecera, antes de decodificarla. Un PNG de pocos KB puede
 * declarar 30000x30000 px (una "bomba de descompresión") y decodificarlo completo agota la memoria.
 */
final class ImagenSegura {

    /** 40 MP cubre cualquier cámara de teléfono actual. */
    static final long MAX_PIXELES = 40_000_000L;

    record Dimensiones(int ancho, int alto) {
    }

    private ImagenSegura() {
    }

    static Dimensiones validar(byte[] datos, String etiqueta) {
        if (datos == null || datos.length == 0) {
            throw new IllegalArgumentException("La imagen de " + etiqueta + " está vacía");
        }
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new IllegalArgumentException("La imagen de " + etiqueta + " no es un formato soportado (JPG/PNG)");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if ((long) ancho * alto > MAX_PIXELES) {
                    throw new IllegalArgumentException("La imagen de " + etiqueta + " mide " + ancho + "x" + alto
                            + " px; el máximo es " + (MAX_PIXELES / 1_000_000) + " megapíxeles");
                }
                return new Dimensiones(ancho, alto);
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer la imagen de " + etiqueta, e);
        }
    }
}
