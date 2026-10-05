package com.veripay.biometria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.veripay.common.NegocioException;

/** Retratos de dominio público de NASA (ver src/test/resources/rostros/FUENTES.md). */
class ComparadorFacialTest {

    private static ComparadorFacial comparador;

    @BeforeAll
    static void cargarModelos() {
        comparador = new ComparadorFacial();
    }

    private static byte[] foto(String nombre) throws IOException {
        try (InputStream in = ComparadorFacialTest.class.getResourceAsStream("/rostros/" + nombre)) {
            return in.readAllBytes();
        }
    }

    private static BigDecimal umbral() {
        return comparador.umbralRecomendado();
    }

    @Test
    void mismaPersonaConDiezAnosDeDiferenciaCoincide() throws IOException {
        // Como una INE de hace años contra una selfie de hoy
        assertThat(comparador.comparar(foto("koch-2013.jpg"), foto("koch-2023.jpg"))).isGreaterThanOrEqualTo(umbral());
    }

    @Test
    void mismaPersonaConRopaYFondoDistintosCoincide() throws IOException {
        assertThat(comparador.comparar(foto("koch-2023.jpg"), foto("koch-traje.jpg"))).isGreaterThanOrEqualTo(umbral());
    }

    @Test
    void personasDistintasConMismoTrajeYFondoNoCoinciden() throws IOException {
        // Si el comparador midiera la escena en vez del rostro, este par pasaría
        assertThat(comparador.comparar(foto("koch-traje.jpg"), foto("meir-traje.jpg"))).isLessThan(umbral());
    }

    @Test
    void personasDistintasNoCoinciden() throws IOException {
        assertThat(comparador.comparar(foto("koch-2023.jpg"), foto("kelly.jpg"))).isLessThan(umbral());
        assertThat(comparador.comparar(foto("koch-2013.jpg"), foto("meir-traje.jpg"))).isLessThan(umbral());
    }

    @Test
    void imagenSinRostroSeRechazaSinContarComoVerificacion() throws IOException {
        byte[] figura = Imagenes.rostro(400, 520, false);
        assertThatThrownBy(() -> comparador.comparar(figura, foto("kelly.jpg")))
                .isInstanceOf(NegocioException.class)
                .extracting("codigo").isEqualTo("ROSTRO_NO_DETECTADO");
    }

    @Test
    void bombaDeDescompresionSeRechazaAntesDeDecodificar() throws IOException {
        byte[] bomba = Imagenes.bombaPng(10_000);
        assertThatThrownBy(() -> comparador.comparar(bomba, foto("kelly.jpg")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000x10000");
    }
}
