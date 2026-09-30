package com.veripay.cliente;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ClienteDtos {

    private ClienteDtos() {
    }

    public record AltaClienteRequest(
            @NotBlank @CurpValida String curp,
            @Pattern(regexp = "^[A-ZÑ&]{4}\\d{6}[A-Z\\d]{3}$", message = "RFC de persona física inválido") String rfc,
            @NotBlank @Size(max = 80) String nombre,
            @NotBlank @Size(max = 80) String apellidoPaterno,
            @Size(max = 80) String apellidoMaterno,
            @NotNull @Past LocalDate fechaNacimiento,
            @NotBlank @Email @Size(max = 120) String email,
            @Pattern(regexp = "^\\d{10}$", message = "El teléfono debe tener 10 dígitos") String telefono) {
    }

    public record ContactoRequest(
            @NotBlank @Email @Size(max = 120) String email,
            @Pattern(regexp = "^\\d{10}$", message = "El teléfono debe tener 10 dígitos") String telefono) {
    }

    public record ClienteResponse(
            Long id, String curp, String rfc, String nombre, String apellidoPaterno, String apellidoMaterno,
            String nombreCompleto, LocalDate fechaNacimiento, String email, String telefono,
            EstadoKyc estadoKyc, Instant creadoEn) {

        public static ClienteResponse de(Cliente c) {
            return new ClienteResponse(c.getId(), c.getCurp(), c.getRfc(), c.getNombre(), c.getApellidoPaterno(),
                    c.getApellidoMaterno(), c.getNombreCompleto(), c.getFechaNacimiento(), c.getEmail(),
                    c.getTelefono(), c.getEstadoKyc(), c.getCreadoEn());
        }
    }
}
