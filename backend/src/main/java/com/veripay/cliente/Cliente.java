package com.veripay.cliente;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 18)
    private String curp;

    @Column(length = 13)
    private String rfc;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "apellido_paterno", nullable = false, length = 80)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 80)
    private String apellidoMaterno;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(length = 15)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_kyc", nullable = false, length = 20)
    private EstadoKyc estadoKyc = EstadoKyc.PENDIENTE;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    @Version
    private long version;

    protected Cliente() {
    }

    public Cliente(String curp, String rfc, String nombre, String apellidoPaterno, String apellidoMaterno,
            LocalDate fechaNacimiento, String email, String telefono) {
        this.curp = curp;
        this.rfc = rfc;
        this.nombre = nombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.fechaNacimiento = fechaNacimiento;
        this.email = email;
        this.telefono = telefono;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }

    public void actualizarContacto(String email, String telefono) {
        this.email = email;
        this.telefono = telefono;
    }

    public void marcarKyc(EstadoKyc estado) {
        this.estadoKyc = estado;
    }

    public String getNombreCompleto() {
        return apellidoMaterno == null || apellidoMaterno.isBlank()
                ? nombre + " " + apellidoPaterno
                : nombre + " " + apellidoPaterno + " " + apellidoMaterno;
    }

    public Long getId() { return id; }
    public String getCurp() { return curp; }
    public String getRfc() { return rfc; }
    public String getNombre() { return nombre; }
    public String getApellidoPaterno() { return apellidoPaterno; }
    public String getApellidoMaterno() { return apellidoMaterno; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public EstadoKyc getEstadoKyc() { return estadoKyc; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
