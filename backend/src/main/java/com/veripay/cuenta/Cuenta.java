package com.veripay.cuenta;

import java.math.BigDecimal;
import java.time.Instant;

import com.veripay.cliente.Cliente;
import com.veripay.common.NegocioException;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "cuenta")
public class Cuenta {

    public enum Estado { ACTIVA, BLOQUEADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 18)
    private String clabe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, length = 3)
    private String moneda = "MXN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ACTIVA;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    @Version
    private long version;

    protected Cuenta() {
    }

    public Cuenta(String clabe, Cliente cliente) {
        this.clabe = clabe;
        this.cliente = cliente;
    }

    public void abonar(BigDecimal monto) {
        validarOperable();
        saldo = saldo.add(monto);
    }

    public void cargar(BigDecimal monto) {
        validarOperable();
        if (saldo.compareTo(monto) < 0) {
            throw new NegocioException("SALDO_INSUFICIENTE",
                    "Saldo insuficiente en la cuenta " + clabe + " (disponible " + saldo + ")");
        }
        saldo = saldo.subtract(monto);
    }

    public void bloquear() {
        estado = Estado.BLOQUEADA;
    }

    public void desbloquear() {
        estado = Estado.ACTIVA;
    }

    private void validarOperable() {
        if (estado != Estado.ACTIVA) {
            throw new NegocioException("CUENTA_BLOQUEADA", "La cuenta " + clabe + " está bloqueada");
        }
    }

    public Long getId() { return id; }
    public String getClabe() { return clabe; }
    public Cliente getCliente() { return cliente; }
    public BigDecimal getSaldo() { return saldo; }
    public String getMoneda() { return moneda; }
    public Estado getEstado() { return estado; }
    public Instant getCreadoEn() { return creadoEn; }
}
