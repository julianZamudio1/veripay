package com.veripay.biometria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ComparadorPerceptualTest {

    private final ComparadorPerceptual comparador = new ComparadorPerceptual();

    @Test
    void mismaImagenEnDistintoTamanoEsCasiIdentica() throws IOException {
        byte[] original = rostro(200, 260, false);
        byte[] reducida = rostro(100, 130, false);
        assertThat(comparador.comparar(original, reducida)).isGreaterThanOrEqualTo(new BigDecimal("0.90"));
    }

    @Test
    void imagenesDistintasQuedanBajoElUmbral() throws IOException {
        byte[] a = rostro(200, 260, false);
        byte[] b = rostro(200, 260, true);
        assertThat(comparador.comparar(a, b)).isLessThan(new BigDecimal("0.80"));
    }

    @Test
    void rechazaArchivoQueNoEsImagen() {
        assertThatThrownBy(() -> comparador.comparar("hola".getBytes(), "mundo".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Figura sintética; {@code invertida} cambia la distribución de luz para simular otra persona. */
    private static byte[] rostro(int w, int h, boolean invertida) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(invertida ? Color.DARK_GRAY : new Color(235, 225, 210));
        g.fillRect(0, 0, w, h);
        g.setColor(invertida ? new Color(240, 240, 240) : new Color(120, 80, 60));
        g.fillOval(w / 5, h / 6, w * 3 / 5, h * 2 / 3);
        g.setColor(invertida ? Color.BLACK : Color.WHITE);
        g.fillRect(invertida ? 0 : w / 2, 0, w / 2, h / 3);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
