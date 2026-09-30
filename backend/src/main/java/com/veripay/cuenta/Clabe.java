package com.veripay.cuenta;

/**
 * CLABE interbancaria (18 dígitos): banco (3) + plaza (3) + cuenta (11) + dígito de control (1).
 * El dígito de control usa los factores 3, 7, 1 según la norma de Banxico.
 */
public final class Clabe {

    private static final int[] FACTORES = {3, 7, 1};

    private Clabe() {
    }

    public static String construir(String banco, String plaza, String cuenta11) {
        String base = banco + plaza + cuenta11;
        if (!base.matches("\\d{17}")) {
            throw new IllegalArgumentException("Banco (3), plaza (3) y cuenta (11) deben ser numéricos");
        }
        return base + digitoControl(base);
    }

    public static int digitoControl(String clabe17) {
        int suma = 0;
        for (int i = 0; i < 17; i++) {
            int d = clabe17.charAt(i) - '0';
            suma += (d * FACTORES[i % 3]) % 10;
        }
        return (10 - (suma % 10)) % 10;
    }

    public static boolean esValida(String clabe) {
        return clabe != null && clabe.matches("\\d{18}")
                && digitoControl(clabe.substring(0, 17)) == clabe.charAt(17) - '0';
    }
}
