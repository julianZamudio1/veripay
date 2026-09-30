package com.veripay.auditoria;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(nullable = false, length = 40)
    private String entidad;

    @Column(name = "entidad_id", length = 40)
    private String entidadId;

    @Column(length = 500)
    private String detalle;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected EventoAuditoria() {
    }

    public EventoAuditoria(String usuario, String accion, String entidad, String entidadId, String detalle) {
        this.usuario = usuario;
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.detalle = detalle;
    }

    public Long getId() { return id; }
    public String getUsuario() { return usuario; }
    public String getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public String getEntidadId() { return entidadId; }
    public String getDetalle() { return detalle; }
    public Instant getCreadoEn() { return creadoEn; }
}
