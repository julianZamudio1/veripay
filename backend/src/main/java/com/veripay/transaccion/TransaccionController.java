package com.veripay.transaccion;

import java.net.URI;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.veripay.common.Pagina;
import com.veripay.transaccion.TransaccionDtos.OperacionRequest;
import com.veripay.transaccion.TransaccionDtos.Tablero;
import com.veripay.transaccion.TransaccionDtos.TransaccionResponse;
import com.veripay.transaccion.TransaccionDtos.TransferenciaRequest;
import com.veripay.transaccion.TransaccionService.Resultado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transacciones")
public class TransaccionController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** Cabecera de respuesta que avisa que se devolvió la transacción original de la clave (convención de Stripe). */
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final TransaccionService service;

    public TransaccionController(TransaccionService service) {
        this.service = service;
    }

    @PostMapping("/transacciones/depositos")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Depósito a una cuenta")
    public ResponseEntity<TransaccionResponse> depositar(@Valid @RequestBody OperacionRequest req,
            @Parameter(description = "Clave única por operación para reintentos seguros")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String clave, Authentication auth) {
        return creada(service.depositar(req, clave, auth.getName()));
    }

    @PostMapping("/transacciones/retiros")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Retiro de una cuenta")
    public ResponseEntity<TransaccionResponse> retirar(@Valid @RequestBody OperacionRequest req,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String clave, Authentication auth) {
        return creada(service.retirar(req, clave, auth.getName()));
    }

    @PostMapping("/transacciones/transferencias")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Transferencia entre cuentas (envía Idempotency-Key para reintentos seguros)")
    public ResponseEntity<TransaccionResponse> transferir(@Valid @RequestBody TransferenciaRequest req,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String clave, Authentication auth) {
        return creada(service.transferir(req, clave, auth.getName()));
    }

    @GetMapping("/transacciones")
    @Operation(summary = "Movimientos, del más reciente al más antiguo")
    public Pagina<TransaccionResponse> movimientos(@RequestParam(required = false) Long cuentaId,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return Pagina.de(service.movimientos(cuentaId, PageRequest.of(pagina, tamano)));
    }

    @GetMapping("/transacciones/{folio}")
    public TransaccionResponse obtener(@PathVariable String folio) {
        return service.obtener(folio);
    }

    @GetMapping("/tablero")
    @Operation(summary = "Indicadores del día")
    public Tablero tablero() {
        return service.tablero();
    }

    /** 201 + Location. Si la clave de idempotencia ya existía, mismo cuerpo con la cabecera Idempotent-Replayed. */
    private static ResponseEntity<TransaccionResponse> creada(Resultado r) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/transacciones/{folio}").buildAndExpand(r.transaccion().folio()).toUri();
        var respuesta = ResponseEntity.status(HttpStatus.CREATED).location(location);
        if (r.repetida()) {
            respuesta.header(IDEMPOTENT_REPLAYED, "true");
        }
        return respuesta.body(r.transaccion());
    }
}
