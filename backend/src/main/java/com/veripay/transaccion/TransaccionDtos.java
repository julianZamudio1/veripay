package com.veripay.transaccion;

import java.math.BigDecimal;
import java.time.Instant;

import com.veripay.cuenta.Cuenta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TransaccionDtos {

    private TransaccionDtos() {
    }

    public record OperacionRequest(
            @NotBlank @Pattern(regexp = "^\\d{18}$", message = "La CLABE debe tener 18 dígitos") String clabe,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal monto,
            @Size(max = 140) String concepto) {
    }

    public record TransferenciaRequest(
            @NotBlank @Pattern(regexp = "^\\d{18}$", message = "La CLABE debe tener 18 dígitos") String clabeOrigen,
            @NotBlank @Pattern(regexp = "^\\d{18}$", message = "La CLABE debe tener 18 dígitos") String clabeDestino,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal monto,
            @Size(max = 140) String concepto) {
    }

    public record TransaccionResponse(Long id, String folio, Transaccion.Tipo tipo, String clabeOrigen,
            String clabeDestino, BigDecimal monto, String concepto, Transaccion.Estado estado,
            String realizadaPor, Instant creadoEn) {

        public static TransaccionResponse de(Transaccion t) {
            return new TransaccionResponse(t.getId(), t.getFolio(), t.getTipo(), clabe(t.getCuentaOrigen()),
                    clabe(t.getCuentaDestino()), t.getMonto(), t.getConcepto(), t.getEstado(), t.getRealizadaPor(),
                    t.getCreadoEn());
        }

        private static String clabe(Cuenta c) {
            return c == null ? null : c.getClabe();
        }
    }

    public record Tablero(long clientesTotal, long clientesPendientes, long clientesVerificados,
            long clientesRechazados, long cuentas, BigDecimal saldoTotal, long transaccionesHoy,
            BigDecimal volumenHoy) {
    }
}
