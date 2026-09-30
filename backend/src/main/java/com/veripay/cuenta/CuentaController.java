package com.veripay.cuenta;

import java.net.URI;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.veripay.common.Pagina;
import com.veripay.cuenta.CuentaService.CuentaResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cuentas")
public class CuentaController {

    public record CambioEstadoRequest(@NotNull Cuenta.Estado estado) {
    }

    private final CuentaService service;

    public CuentaController(CuentaService service) {
        this.service = service;
    }

    @GetMapping("/cuentas")
    @Operation(summary = "Lista cuentas; filtra por cliente o por CLABE")
    public Pagina<CuentaResponse> listar(@RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String clabe,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return Pagina.de(service.listar(clienteId, clabe,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @GetMapping("/cuentas/{id}")
    public CuentaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/clientes/{clienteId}/cuentas")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Abre una cuenta con CLABE para un cliente con KYC verificado")
    public ResponseEntity<CuentaResponse> abrir(@PathVariable Long clienteId, Authentication auth) {
        CuentaResponse cuenta = service.abrir(clienteId, auth.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/cuentas/{id}").buildAndExpand(cuenta.id()).toUri();
        return ResponseEntity.created(location).body(cuenta);
    }

    @PatchMapping("/cuentas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bloquea o desbloquea una cuenta: {\"estado\": \"BLOQUEADA\" | \"ACTIVA\"}")
    public CuentaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest req,
            Authentication auth) {
        return service.cambiarEstado(id, req.estado(), auth.getName());
    }
}
