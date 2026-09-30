package com.veripay.cuenta;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veripay.cuenta.CuentaService.CuentaResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/cuentas")
@Tag(name = "Cuentas")
public class CuentaController {

    private final CuentaService service;

    public CuentaController(CuentaService service) {
        this.service = service;
    }

    @GetMapping
    public List<CuentaResponse> listar(@RequestParam(required = false) Long clienteId) {
        return service.listar(clienteId);
    }

    @GetMapping("/clabe/{clabe}")
    public CuentaResponse porClabe(@PathVariable String clabe) {
        return service.porClabe(clabe);
    }

    @PostMapping("/cliente/{clienteId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Abre una cuenta con CLABE para un cliente con KYC verificado")
    public CuentaResponse abrir(@PathVariable Long clienteId, Authentication auth) {
        return service.abrir(clienteId, auth.getName());
    }

    @PostMapping("/{id}/bloqueo")
    @PreAuthorize("hasRole('ADMIN')")
    public CuentaResponse bloquear(@PathVariable Long id, Authentication auth) {
        return service.cambiarEstado(id, true, auth.getName());
    }

    @PostMapping("/{id}/desbloqueo")
    @PreAuthorize("hasRole('ADMIN')")
    public CuentaResponse desbloquear(@PathVariable Long id, Authentication auth) {
        return service.cambiarEstado(id, false, auth.getName());
    }
}
