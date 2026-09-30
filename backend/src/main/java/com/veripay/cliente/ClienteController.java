package com.veripay.cliente;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.veripay.biometria.KycService;
import com.veripay.biometria.VerificacionBiometrica;
import com.veripay.cliente.ClienteDtos.AltaClienteRequest;
import com.veripay.cliente.ClienteDtos.ClienteResponse;
import com.veripay.cliente.ClienteDtos.ContactoRequest;
import com.veripay.common.Pagina;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
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
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        var page = service.buscar(texto, estado,
                PageRequest.of(pagina, Math.min(tamano, 100), Sort.by(Sort.Direction.DESC, "id")));
        return Pagina.de(page, ClienteResponse::de);
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return ClienteResponse.de(service.obtener(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Alta de cliente (queda en KYC PENDIENTE)")
    public ClienteResponse alta(@Valid @RequestBody AltaClienteRequest req, Authentication auth) {
        return ClienteResponse.de(service.alta(req, auth.getName()));
    }

    @PutMapping("/{id}/contacto")
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    public ClienteResponse actualizarContacto(@PathVariable Long id, @Valid @RequestBody ContactoRequest req,
            Authentication auth) {
        return ClienteResponse.de(service.actualizarContacto(id, req, auth.getName()));
    }

    @PostMapping(path = "/{id}/verificaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ANALISTA')")
    @Operation(summary = "Verificación biométrica: foto de la identificación vs. selfie")
    public VerificacionResponse verificar(@PathVariable Long id,
            @RequestPart("identificacion") MultipartFile identificacion,
            @RequestPart("selfie") MultipartFile selfie,
            Authentication auth) throws IOException {
        return VerificacionResponse.de(kyc.verificar(id, identificacion.getBytes(), selfie.getBytes(), auth.getName()));
    }

    @GetMapping("/{id}/verificaciones")
    public List<VerificacionResponse> historialVerificaciones(@PathVariable Long id) {
        return kyc.historial(id).stream().map(VerificacionResponse::de).toList();
    }
}
