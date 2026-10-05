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

    /** CURP leída de la INE por OCR (nula en verificaciones anteriores a la lectura de la credencial). */
    @Column(name = "curp_ine", length = 18)
    private String curpIne;

    @Column(name = "curp_coincide")
    private Boolean curpCoincide;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected VerificacionBiometrica() {
    }

    /** Se aprueba solo si el rostro coincide y la CURP de la INE es la del cliente. */
    public VerificacionBiometrica(Cliente cliente, BigDecimal puntaje, BigDecimal umbral, String curpIne,
            String huellaIdentificacion, String huellaSelfie, String realizadaPor) {
        this.cliente = cliente;
        this.puntaje = puntaje;
        this.umbral = umbral;
        this.curpIne = curpIne;
        this.curpCoincide = cliente.getCurp().equals(curpIne);
        this.aprobada = isRostroCoincide() && curpCoincide;
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
    public String getCurpIne() { return curpIne; }
    public Boolean getCurpCoincide() { return curpCoincide; }
    public boolean isRostroCoincide() { return puntaje.compareTo(umbral) >= 0; }
}
