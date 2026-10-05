package com.veripay.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CurpTest {

    private static String completa(String curp17) {
        return curp17 + Curp.digitoVerificador(curp17);
    }

    @Test
    void aceptaCurpConDigitoVerificadorCorrecto() {
        assertThat(Curp.esValida(completa("GOMA850312HDFRRN0"))).isTrue();
    }

    @Test
    void rechazaDigitoVerificadorIncorrecto() {
        String curp = completa("GOMA850312HDFRRN0");
        int otro = (Character.getNumericValue(curp.charAt(17)) + 1) % 10;
        assertThat(Curp.esValida(curp.substring(0, 17) + otro)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "GOMA850312HDF", "GOMA851312HDFRRN01", "GOMA850312HXXRRN01", "1OMA850312HDFRRN01"})
    void rechazaFormatosInvalidos(String curp) {
        assertThat(Curp.esValida(curp)).isFalse();
    }

    @Test
    void rechazaFechaInexistente() {
        assertThat(Curp.esValida(completa("GOMA850231HDFRRN0"))).isFalse();
    }

    @Test
    void deduceSigloPorHomoclave() {
        assertThat(Curp.fechaNacimiento(completa("GOMA850312HDFRRN0"))).contains(LocalDate.of(1985, 3, 12));
        assertThat(Curp.fechaNacimiento(completa("RAMC010415HNLMRRA"))).contains(LocalDate.of(2001, 4, 15));
    }

    @Test
    void rechazaFechaDeNacimientoFutura() {
        // Homoclave con letra = nacido en 2000 o después: 85 se leería como 2085
        assertThat(Curp.esValida(completa("GOMA850312HDFRRNO"))).isFalse();
    }
}
