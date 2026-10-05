package com.veripay.biometria;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Similitud por hashes perceptuales (aHash + dHash de 64 bits) sobre la imagen completa.
 * NO reconoce rostros: solo aprueba la misma foto reescalada o recomprimida. Se conserva para
 * pruebas automatizadas con imágenes sintéticas ({@code veripay.biometria.motor=perceptual}).
 */
@Component
@ConditionalOnProperty(name = "veripay.biometria.motor", havingValue = "perceptual")
public class ComparadorPerceptual implements ComparadorBiometrico {

    /** Umbral calibrado para hashes perceptuales: similitud de bits entre 0 y 1. */
    static final BigDecimal UMBRAL = new BigDecimal("0.80");
    /** Lado aproximado al que se reduce la imagen al decodificarla. */
    static final int LADO_DECODIFICADO = 256;

    @Override
    public BigDecimal umbralRecomendado() {
        return UMBRAL;
    }

    @Override
    public BigDecimal comparar(byte[] imagenIdentificacion, byte[] imagenSelfie) {
        BufferedImage a = leer(imagenIdentificacion, "identificación");
        BufferedImage b = leer(imagenSelfie, "selfie");

        double simAHash = similitud(aHash(a), aHash(b));
        double simDHash = similitud(dHash(a), dHash(b));
        double puntaje = (simAHash + simDHash) / 2.0;
        return BigDecimal.valueOf(puntaje).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Valida dimensiones sin decodificar (ver {@link ImagenSegura}) y decodifica con submuestreo:
     * los hashes solo necesitan ~9x8 píxeles.
     */
    static BufferedImage leer(byte[] datos, String etiqueta) {
        ImagenSegura.Dimensiones dim = ImagenSegura.validar(datos, etiqueta);
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            ImageReader lector = ImageIO.getImageReaders(entrada).next();
            try {
                lector.setInput(entrada, true, true);
                ImageReadParam param = lector.getDefaultReadParam();
                int paso = Math.max(1, Math.max(dim.ancho(), dim.alto()) / LADO_DECODIFICADO);
                param.setSourceSubsampling(paso, paso, 0, 0);
                return lector.read(0, param);
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer la imagen de " + etiqueta, e);
        }
    }

    /** Hash promedio: cada bit indica si el píxel (8x8) supera la media. */
    static long aHash(BufferedImage img) {
        int[] px = grises(img, 8, 8);
        long suma = 0;
        for (int p : px) {
            suma += p;
        }
        double media = suma / 64.0;
        long hash = 0;
        for (int i = 0; i < 64; i++) {
            if (px[i] > media) {
                hash |= 1L << i;
            }
        }
        return hash;
    }

    /** Hash de diferencias: cada bit indica si un píxel es más claro que su vecino derecho (9x8). */
    static long dHash(BufferedImage img) {
        int[] px = grises(img, 9, 8);
        long hash = 0;
        int bit = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (px[y * 9 + x] > px[y * 9 + x + 1]) {
                    hash |= 1L << bit;
                }
                bit++;
            }
        }
        return hash;
    }

    private static double similitud(long h1, long h2) {
        return 1.0 - Long.bitCount(h1 ^ h2) / 64.0;
    }

    private static int[] grises(BufferedImage origen, int ancho, int alto) {
        BufferedImage reducida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = reducida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(origen, 0, 0, ancho, alto, null);
        g.dispose();

        int[] px = new int[ancho * alto];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int rgb = reducida.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                px[y * ancho + x] = (int) Math.round(0.299 * r + 0.587 * gr + 0.114 * b);
            }
        }
        return px;
    }
}
