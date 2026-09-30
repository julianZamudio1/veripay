package com.veripay.biometria;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;

/** Imágenes sintéticas para las pruebas de biometría. */
final class Imagenes {

    private Imagenes() {
    }

    /** Figura tipo rostro; {@code otraPersona} cambia la distribución de luz para que no coincida. */
    static byte[] rostro(int w, int h, boolean otraPersona) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(otraPersona ? Color.DARK_GRAY : new Color(235, 225, 210));
        g.fillRect(0, 0, w, h);
        g.setColor(otraPersona ? new Color(240, 240, 240) : new Color(120, 80, 60));
        g.fillOval(w / 5, h / 6, w * 3 / 5, h * 2 / 3);
        g.setColor(otraPersona ? Color.BLACK : Color.WHITE);
        g.fillRect(otraPersona ? 0 : w / 2, 0, w / 2, h / 3);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /**
     * PNG en escala de grises que declara {@code lado x lado} píxeles negros. Comprimido pesa
     * cientos de KB, pero decodificado ocupa lado² bytes: una "bomba de descompresión".
     */
    static byte[] bombaPng(int lado) throws IOException {
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(datos, new Deflater(Deflater.BEST_COMPRESSION))) {
            byte[] fila = new byte[lado + 1]; // byte de filtro + píxeles
            for (int y = 0; y < lado; y++) {
                z.write(fila);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(png);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(ihdr);
        h.writeInt(lado);
        h.writeInt(lado);
        h.write(new byte[] {8, 0, 0, 0, 0}); // 8 bits, escala de grises
        chunk(out, "IHDR", ihdr.toByteArray());
        chunk(out, "IDAT", datos.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void chunk(DataOutputStream out, String tipo, byte[] datos) throws IOException {
        byte[] t = tipo.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(datos);
        out.writeInt(datos.length);
        out.write(t);
        out.write(datos);
        out.writeInt((int) crc.getValue());
    }
}
