package com.veripay.biometria;

import java.math.BigDecimal;
import java.time.Instant;

import com.veripay.cliente.Cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Resultado de una verificación. Por privacidad no se guardan las imágenes,
 * solo su huella SHA-256 para poder demostrar qué se comparó.
 */
@Entity
@Table(name = "verificacion_biometrica")
public class VerificacionBiometrica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal puntaje;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal umbral;

    @Column(nullable = false)
    private boolean aprobada;

    @Column(name = "huella_identificacion", nullable = false, length = 64)
    private String huellaIdentificacion;

    @Column(name = "huella_selfie", nullable = false, length = 64)
    private String huellaSelfie;

    @Column(name = "realizada_por", nullable = false, length = 50)
    private String realizadaPor;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected VerificacionBiometrica() {
    }

    public VerificacionBiometrica(Cliente cliente, BigDecimal puntaje, BigDecimal umbral,
            String huellaIdentificacion, String huellaSelfie, String realizadaPor) {
        this.cliente = cliente;
        this.puntaje = puntaje;
        this.umbral = umbral;
        this.aprobada = puntaje.compareTo(umbral) >= 0;
        this.huellaIdentificacion = huellaIdentificacion;
        this.huellaSelfie = huellaSelfie;
        this.realizadaPor = realizadaPor;
    }

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public BigDecimal getPuntaje() { return puntaje; }
    public BigDecimal getUmbral() { return umbral; }
    public boolean isAprobada() { return aprobada; }
    public String getHuellaIdentificacion() { return huellaIdentificacion; }
    public String getHuellaSelfie() { return huellaSelfie; }
    public String getRealizadaPor() { return realizadaPor; }
    public Instant getCreadoEn() { return creadoEn; }
}
