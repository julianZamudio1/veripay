package com.veripay.cliente;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validación estructural de la CURP (Clave Única de Registro de Población) según RENAPO:
 * formato, fecha de nacimiento, sexo, entidad federativa y dígito verificador.
 */
public final class Curp {

    private static final Pattern FORMATO = Pattern.compile(
            "^[A-Z][AEIOUX][A-Z]{2}"                      // iniciales
            + "\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])" // AAMMDD
            + "[HMX]"                                        // sexo
            + "(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)"
            + "[B-DF-HJ-NP-TV-Z]{3}"                        // consonantes internas
            + "[A-Z\\d]"                                     // homoclave (dígito <2000, letra >=2000)
            + "\\d$");                                       // dígito verificador

    private static final String DICCIONARIO = "0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";

    private Curp() {
    }

    public static boolean esValida(String curp) {
        if (curp == null || !FORMATO.matcher(curp).matches()) {
            return false;
        }
        // Una fecha de nacimiento futura nunca es válida (p. ej. homoclave "O" leída donde había un "0")
        return fechaNacimiento(curp).filter(f -> !f.isAfter(LocalDate.now())).isPresent()
                && digitoVerificador(curp.substring(0, 17)) == Character.getNumericValue(curp.charAt(17));
    }

    /** Calcula el dígito verificador a partir de los primeros 17 caracteres. */
    public static int digitoVerificador(String curp17) {
        if (curp17 == null || curp17.length() != 17) {
            throw new IllegalArgumentException("Se requieren los primeros 17 caracteres de la CURP");
        }
        int suma = 0;
        for (int i = 0; i < 17; i++) {
            int valor = DICCIONARIO.indexOf(curp17.charAt(i));
            if (valor < 0) {
                throw new IllegalArgumentException("Carácter inválido en CURP: " + curp17.charAt(i));
            }
            suma += valor * (18 - i);
        }
        int digito = 10 - (suma % 10);
        return digito == 10 ? 0 : digito;
    }

    /** Fecha de nacimiento codificada; el siglo se deduce de la homoclave (posición 17). */
    public static Optional<LocalDate> fechaNacimiento(String curp) {
        try {
            int aa = Integer.parseInt(curp.substring(4, 6));
            int mm = Integer.parseInt(curp.substring(6, 8));
            int dd = Integer.parseInt(curp.substring(8, 10));
            int siglo = Character.isDigit(curp.charAt(16)) ? 1900 : 2000;
            return Optional.of(LocalDate.of(siglo + aa, mm, dd));
        } catch (DateTimeException | NumberFormatException | IndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }
}
