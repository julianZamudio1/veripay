package com.veripay.transaccion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.veripay.cuenta.Cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaccion")
public class Transaccion {

    public enum Tipo { DEPOSITO, RETIRO, TRANSFERENCIA }

    public enum Estado { APLICADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String folio = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_origen_id")
    private Cuenta cuentaOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_destino_id")
    private Cuenta cuentaDestino;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(length = 140)
    private String concepto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.APLICADA;

    @Column(name = "clave_idempotencia", unique = true, length = 64)
    private String claveIdempotencia;

    @Column(name = "realizada_por", nullable = false, length = 50)
    private String realizadaPor;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected Transaccion() {
    }

    public Transaccion(Tipo tipo, Cuenta origen, Cuenta destino, BigDecimal monto, String concepto,
            String claveIdempotencia, String realizadaPor) {
        this.tipo = tipo;
        this.cuentaOrigen = origen;
        this.cuentaDestino = destino;
        this.monto = monto;
        this.concepto = concepto;
        this.claveIdempotencia = claveIdempotencia;
        this.realizadaPor = realizadaPor;
    }

    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Tipo getTipo() { return tipo; }
    public Cuenta getCuentaOrigen() { return cuentaOrigen; }
    public Cuenta getCuentaDestino() { return cuentaDestino; }
    public BigDecimal getMonto() { return monto; }
    public String getConcepto() { return concepto; }
    public Estado getEstado() { return estado; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public String getRealizadaPor() { return realizadaPor; }
    public Instant getCreadoEn() { return creadoEn; }
}
