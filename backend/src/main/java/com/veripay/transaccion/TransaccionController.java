package com.veripay.transaccion;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.veripay.common.Pagina;
import com.veripay.transaccion.TransaccionDtos.OperacionRequest;
import com.veripay.transaccion.TransaccionDtos.Tablero;
import com.veripay.transaccion.TransaccionDtos.TransaccionResponse;
import com.veripay.transaccion.TransaccionDtos.TransferenciaRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Transacciones")
public class TransaccionController {

    private static final String IDEMPOTENCY = "Idempotency-Key";

    private final TransaccionService service;

    public TransaccionController(TransaccionService service) {
        this.service = service;
    }

    @PostMapping("/transacciones/depositos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    public TransaccionResponse depositar(@Valid @RequestBody OperacionRequest req,
            @RequestHeader(name = IDEMPOTENCY, required = false) String clave, Authentication auth) {
        return service.depositar(req, clave, auth.getName());
    }

    @PostMapping("/transacciones/retiros")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    public TransaccionResponse retirar(@Valid @RequestBody OperacionRequest req,
            @RequestHeader(name = IDEMPOTENCY, required = false) String clave, Authentication auth) {
        return service.retirar(req, clave, auth.getName());
    }

    @PostMapping("/transacciones/transferencias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Transferencia entre cuentas (envía Idempotency-Key para reintentos seguros)")
    public TransaccionResponse transferir(@Valid @RequestBody TransferenciaRequest req,
            @RequestHeader(name = IDEMPOTENCY, required = false) String clave, Authentication auth) {
        return service.transferir(req, clave, auth.getName());
    }

    @GetMapping("/transacciones")
    public Pagina<TransaccionResponse> movimientos(@RequestParam(required = false) Long cuentaId,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(service.movimientos(cuentaId, PageRequest.of(pagina, Math.min(tamano, 100))));
    }

    @GetMapping("/tablero")
    public Tablero tablero() {
        return service.tablero();
    }
}
