package com.veripay.biometria;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.veripay.cliente.Curp;

class LectorCurpOcrTest {

    /** CURP válida (dígito verificador correcto) que no pertenece a nadie: solo para pruebas. */
    private static final String CURP = "GOMA850312HDFRRN0" + Curp.digitoVerificador("GOMA850312HDFRRN0");

    private static LectorCurpOcr lector;

    @BeforeAll
    static void cargarModelos() {
        lector = new LectorCurpOcr();
    }

    // ---- Corrección por posición (sin OCR)

    @Test
    void corrigeConfusionesTipicasSegunLaPosicion() {
        // 0 en lugar de O (posición de letra), O e I en lugar de 0 y 1 (posiciones de fecha)
        String leida = "G0MA85O3I2" + CURP.substring(10);
        assertThat(LectorCurpOcr.buscarCurp(leida)).contains(CURP);
    }

    @Test
    void encuentraLaCurpPegadaAlRotulo() {
        assertThat(LectorCurpOcr.buscarCurp("CURP" + CURP)).contains(CURP);
        assertThat(LectorCurpOcr.buscarCurp("curp: " + CURP.toLowerCase() + " 2022")).contains(CURP);
    }

    @Test
    void resuelveHomoclaveAmbiguaPorEdadPlausible() {
        // "O" y "0" en la homoclave dan el mismo dígito verificador; con "O" la persona nacería en 2085
        String leida = CURP.substring(0, 16) + "O" + CURP.charAt(17);
        assertThat(LectorCurpOcr.buscarCurp(leida)).contains(CURP);
    }

    @Test
    void rechazaLecturasConDigitoVerificadorIncorrecto() {
        int otro = (Character.getNumericValue(CURP.charAt(17)) + 1) % 10;
        assertThat(LectorCurpOcr.buscarCurp(CURP.substring(0, 17) + otro)).isEmpty();
    }

    @Test
    void noInventaCurpsEnTextoComun() {
        assertThat(LectorCurpOcr.buscarCurp("INSTITUTONACIONALELECTORAL")).isEmpty();
        assertThat(LectorCurpOcr.buscarCurp("CLAVEDEELECTORZMGVED04042216H200")).isEmpty();
    }

    // ---- Extremo a extremo: detección (PP-OCRv3) + lectura (Tesseract) sobre una credencial sintética

    @Test
    void leeLaCurpDeUnaCredencialInclinada() throws IOException {
        assertThat(lector.leerCurp(credencial(CURP, 2.5))).contains(CURP);
    }

    @Test
    void noDevuelveNadaSiLaCurpImpresaEsInvalida() throws IOException {
        int otro = (Character.getNumericValue(CURP.charAt(17)) + 1) % 10;
        assertThat(lector.leerCurp(credencial(CURP.substring(0, 17) + otro, 0))).isEmpty();
    }

    @Test
    void noDevuelveNadaEnUnaFotoSinCredencial() throws IOException {
        try (var in = getClass().getResourceAsStream("/rostros/kelly.jpg")) {
            assertThat(lector.leerCurp(in.readAllBytes())).isEmpty();
        }
    }

    /** Dibuja una credencial con rótulos y datos como los de una INE, girada unos grados. */
    private static byte[] credencial(String curp, double grados) throws IOException {
        BufferedImage img = new BufferedImage(1300, 820, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(70, 72, 78));
        g.fillRect(0, 0, 1300, 820);
        g.rotate(Math.toRadians(grados), 650, 410);
        g.setColor(new Color(238, 236, 228));
        g.fillRoundRect(110, 110, 1080, 600, 40, 40);
        g.setColor(new Color(40, 40, 40));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString("CREDENCIAL PARA VOTAR", 380, 190);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.drawString("NOMBRE", 470, 270);
        g.drawString("GOMEZ MARTINEZ ANDRES", 470, 305);
        g.drawString("CURP", 470, 500);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
        g.drawString(curp, 470, 545);
        g.setColor(new Color(150, 145, 140));
        g.fillRect(160, 250, 250, 320);   // lugar de la fotografía
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
