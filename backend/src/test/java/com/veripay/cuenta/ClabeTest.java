package com.veripay.cuenta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClabeTest {

    @Test
    void calculaDigitoDeControlConocido() {
        // CLABE de ejemplo usada comúnmente en validadores: 032 180 00011835971 -> dígito 9
        assertThat(Clabe.construir("032", "180", "00011835971")).isEqualTo("032180000118359719");
    }

    @Test
    void validaClabeGenerada() {
        String clabe = Clabe.construir("646", "180", "00000012345");
        assertThat(clabe).hasSize(18);
        assertThat(Clabe.esValida(clabe)).isTrue();
    }

    @Test
    void detectaDigitoAlterado() {
        String clabe = Clabe.construir("646", "180", "00000012345");
        char alterado = clabe.charAt(17) == '9' ? '0' : (char) (clabe.charAt(17) + 1);
        assertThat(Clabe.esValida(clabe.substring(0, 17) + alterado)).isFalse();
    }

    @Test
    void rechazaEntradaNoNumerica() {
        assertThatThrownBy(() -> Clabe.construir("64A", "180", "00000012345"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
