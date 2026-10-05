package com.veripay.biometria;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * OpenCV y Tesseract solo cargan modelos desde el sistema de archivos. Esta clase copia los que
 * vienen dentro del JAR/WAR a una carpeta temporal, así funciona igual en Tomcat y en WildFly.
 */
final class Modelos {

    private Modelos() {
    }

    /** Copia un recurso del classpath a un archivo temporal y devuelve su ruta. */
    static Path extraer(String recurso) {
        try {
            Path destino = Files.createTempFile("veripay-", "-" + Path.of(recurso).getFileName());
            copiar(recurso, destino);
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo preparar el modelo " + recurso, e);
        }
    }

    /** Copia varios recursos a una carpeta temporal nueva (Tesseract espera una carpeta "tessdata"). */
    static Path extraerCarpeta(String... recursos) {
        try {
            Path carpeta = Files.createTempDirectory("veripay-tessdata-");
            carpeta.toFile().deleteOnExit();
            for (String recurso : recursos) {
                copiar(recurso, carpeta.resolve(Path.of(recurso).getFileName().toString()));
            }
            return carpeta;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudieron preparar los modelos", e);
        }
    }

    private static void copiar(String recurso, Path destino) throws IOException {
        try (InputStream in = Modelos.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("No se encontró el modelo " + recurso + " en el classpath");
            }
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            destino.toFile().deleteOnExit();
        }
    }
}
