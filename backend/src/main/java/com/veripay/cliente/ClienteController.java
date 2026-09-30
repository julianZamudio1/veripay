package com.veripay.cliente;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.veripay.biometria.KycService;
import com.veripay.biometria.VerificacionBiometrica;
import com.veripay.cliente.ClienteDtos.AltaClienteRequest;
import com.veripay.cliente.ClienteDtos.ClienteResponse;
import com.veripay.cliente.ClienteDtos.ContactoRequest;
import com.veripay.common.Pagina;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes y KYC")
public class ClienteController {

    public record VerificacionResponse(Long id, BigDecimal puntaje, BigDecimal umbral, boolean aprobada,
            String huellaIdentificacion, String huellaSelfie, String realizadaPor, Instant creadoEn) {

        static VerificacionResponse de(VerificacionBiometrica v) {
            return new VerificacionResponse(v.getId(), v.getPuntaje(), v.getUmbral(), v.isAprobada(),
                    v.getHuellaIdentificacion(), v.getHuellaSelfie(), v.getRealizadaPor(), v.getCreadoEn());
        }
    }

    private final ClienteService service;
    private final KycService kyc;

    public ClienteController(ClienteService service, KycService kyc) {
        this.service = service;
        this.kyc = kyc;
    }

    @GetMapping
    @Operation(summary = "Busca clientes por nombre, CURP o correo")
    public Pagina<ClienteResponse> buscar(@RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoKyc estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int tamano) {
        var page = service.buscar(texto, estado,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));
        return Pagina.de(page, ClienteResponse::de);
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return ClienteResponse.de(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Alta de cliente (queda en KYC PENDIENTE)")
    public ResponseEntity<ClienteResponse> alta(@Valid @RequestBody AltaClienteRequest req, Authentication auth) {
        Cliente cliente = service.alta(req, auth.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(cliente.getId()).toUri();
        return ResponseEntity.created(location).body(ClienteResponse.de(cliente));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Actualiza los datos de contacto (correo y teléfono)")
    public ClienteResponse actualizarContacto(@PathVariable Long id, @Valid @RequestBody ContactoRequest req,
            Authentication auth) {
        return ClienteResponse.de(service.actualizarContacto(id, req, auth.getName()));
    }

    @PostMapping(path = "/{id}/verificaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Verificación biométrica: foto de la identificación vs. selfie")
    public ResponseEntity<VerificacionResponse> verificar(@PathVariable Long id,
            @RequestPart("identificacion") MultipartFile identificacion,
            @RequestPart("selfie") MultipartFile selfie,
            Authentication auth) throws IOException {
        VerificacionBiometrica v = kyc.verificar(id, identificacion.getBytes(), selfie.getBytes(), auth.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{verificacionId}").buildAndExpand(v.getId()).toUri();
        return ResponseEntity.created(location).body(VerificacionResponse.de(v));
    }

    @GetMapping("/{id}/verificaciones")
    public List<VerificacionResponse> historialVerificaciones(@PathVariable Long id) {
        return kyc.historial(id).stream().map(VerificacionResponse::de).toList();
    }

    @GetMapping("/{id}/verificaciones/{verificacionId}")
    public VerificacionResponse obtenerVerificacion(@PathVariable Long id, @PathVariable Long verificacionId) {
        return VerificacionResponse.de(kyc.obtener(id, verificacionId));
    }
}
