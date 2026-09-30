package com.veripay.biometria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class ComparadorPerceptualTest {

    private final ComparadorPerceptual comparador = new ComparadorPerceptual();

    @Test
    void mismaImagenEnDistintoTamanoEsCasiIdentica() throws IOException {
        byte[] original = Imagenes.rostro(200, 260, false);
        byte[] reducida = Imagenes.rostro(100, 130, false);
        assertThat(comparador.comparar(original, reducida)).isGreaterThanOrEqualTo(new BigDecimal("0.90"));
    }

    @Test
    void imagenesDistintasQuedanBajoElUmbral() throws IOException {
        byte[] a = Imagenes.rostro(200, 260, false);
        byte[] b = Imagenes.rostro(200, 260, true);
        assertThat(comparador.comparar(a, b)).isLessThan(new BigDecimal("0.80"));
    }

    @Test
    void rechazaArchivoQueNoEsImagen() {
        assertThatThrownBy(() -> comparador.comparar("hola".getBytes(), "mundo".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaBombaDeDescompresionSinDecodificarla() throws IOException {
        // 10000x10000 = 100 MP declarados en un archivo pequeño; antes esto agotaba la memoria de la JVM
        byte[] bomba = Imagenes.bombaPng(10_000);
        assertThat(bomba.length).isLessThan(200_000);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> comparador.comparar(bomba, Imagenes.rostro(200, 260, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000x10000");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void imagenGrandeDentroDelLimiteSeDecodificaSubmuestreada() throws IOException {
        byte[] grande = Imagenes.bombaPng(4_000); // 16 MP, dentro del límite
        assertThat(ComparadorPerceptual.leer(grande, "prueba").getWidth()).isLessThanOrEqualTo(500);
    }
}
