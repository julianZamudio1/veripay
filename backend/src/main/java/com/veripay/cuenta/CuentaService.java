package com.veripay.cuenta;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.auditoria.AuditoriaService;
import com.veripay.cliente.Cliente;
import com.veripay.cliente.ClienteService;
import com.veripay.cliente.EstadoKyc;
import com.veripay.common.NegocioException;
import com.veripay.common.RecursoNoEncontradoException;
import com.veripay.config.VeriPayProperties;

@Service
public class CuentaService {

    public record CuentaResponse(Long id, String clabe, Long clienteId, String titular, BigDecimal saldo,
            String moneda, Cuenta.Estado estado, Instant creadoEn) {

        public static CuentaResponse de(Cuenta c) {
            return new CuentaResponse(c.getId(), c.getClabe(), c.getCliente().getId(),
                    c.getCliente().getNombreCompleto(), c.getSaldo(), c.getMoneda(), c.getEstado(), c.getCreadoEn());
        }
    }

    static final int MAX_CUENTAS_POR_CLIENTE = 3;

    private final CuentaRepository repository;
    private final ClienteService clientes;
    private final AuditoriaService auditoria;
    private final VeriPayProperties.Banco banco;
    private final SecureRandom random = new SecureRandom();

    public CuentaService(CuentaRepository repository, ClienteService clientes, AuditoriaService auditoria,
            VeriPayProperties props) {
        this.repository = repository;
        this.clientes = clientes;
        this.auditoria = auditoria;
        this.banco = props.banco();
    }

    @Transactional
    public CuentaResponse abrir(Long clienteId, String usuario) {
        // Bloquea al cliente: dos aperturas simultáneas no pueden rebasar el límite de cuentas
        Cliente cliente = clientes.obtenerParaActualizar(clienteId);
        if (cliente.getEstadoKyc() != EstadoKyc.VERIFICADO) {
            throw new NegocioException("KYC_REQUERIDO",
                    "El cliente debe tener la identidad verificada para abrir una cuenta");
        }
        if (repository.countByClienteId(clienteId) >= MAX_CUENTAS_POR_CLIENTE) {
            throw new NegocioException("LIMITE_CUENTAS",
                    "Un cliente puede tener como máximo " + MAX_CUENTAS_POR_CLIENTE + " cuentas");
        }
        Cuenta cuenta = repository.save(new Cuenta(nuevaClabe(), cliente));
        auditoria.registrar(usuario, "APERTURA_CUENTA", "CUENTA", cuenta.getId(), "CLABE " + cuenta.getClabe());
        return CuentaResponse.de(cuenta);
    }

    @Transactional
    public CuentaResponse cambiarEstado(Long cuentaId, Cuenta.Estado estado, String usuario) {
        Cuenta cuenta = repository.findByIdParaActualizar(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", cuentaId));
        if (cuenta.getEstado() != estado) {
            if (estado == Cuenta.Estado.BLOQUEADA) {
                cuenta.bloquear();
            } else {
                cuenta.desbloquear();
            }
            auditoria.registrar(usuario, estado == Cuenta.Estado.BLOQUEADA ? "BLOQUEO_CUENTA" : "DESBLOQUEO_CUENTA",
                    "CUENTA", cuentaId, null);
        }
        return CuentaResponse.de(cuenta);
    }

    @Transactional(readOnly = true)
    public CuentaResponse obtener(Long id) {
        return CuentaResponse.de(repository.findConClienteById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", id)));
    }

    /** Filtra por CLABE (0 o 1 resultado), por cliente o devuelve todas, siempre paginado. */
    @Transactional(readOnly = true)
    public Page<CuentaResponse> listar(Long clienteId, String clabe, Pageable pageable) {
        if (clabe != null && !clabe.isBlank()) {
            List<CuentaResponse> una = repository.findByClabe(clabe.trim())
                    .filter(c -> clienteId == null || c.getCliente().getId().equals(clienteId))
                    .map(CuentaResponse::de).stream().toList();
            return new PageImpl<>(una, pageable, una.size());
        }
        Page<Cuenta> pagina = clienteId == null ? repository.pagina(pageable)
                : repository.paginaPorCliente(clienteId, pageable);
        return pagina.map(CuentaResponse::de);
    }

    private String nuevaClabe() {
        for (int intento = 0; intento < 10; intento++) {
            String cuenta11 = String.format("%011d", Math.floorMod(random.nextLong(), 100_000_000_000L));
            String clabe = Clabe.construir(banco.claveBanco(), banco.clavePlaza(), cuenta11);
            if (!repository.existsByClabe(clabe)) {
                return clabe;
            }
        }
        throw new IllegalStateException("No se pudo generar una CLABE única");
    }
}
