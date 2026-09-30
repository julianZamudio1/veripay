package com.veripay.transaccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.auditoria.AuditoriaService;
import com.veripay.cliente.ClienteRepository;
import com.veripay.cliente.EstadoKyc;
import com.veripay.common.ConflictoException;
import com.veripay.common.NegocioException;
import com.veripay.common.RecursoNoEncontradoException;
import com.veripay.cuenta.Cuenta;
import com.veripay.cuenta.CuentaRepository;
import com.veripay.transaccion.TransaccionDtos.OperacionRequest;
import com.veripay.transaccion.TransaccionDtos.Tablero;
import com.veripay.transaccion.TransaccionDtos.TransaccionResponse;
import com.veripay.transaccion.TransaccionDtos.TransferenciaRequest;

/**
 * Movimientos de dinero. Reglas clave:
 * <ul>
 *   <li>Los saldos se modifican con bloqueo pesimista, tomando las cuentas siempre en orden de id
 *       para evitar interbloqueos entre transferencias cruzadas.</li>
 *   <li>La cabecera {@code Idempotency-Key} hace que un reintento del cliente no duplique el cargo.
 *       Reutilizar la clave con otros datos o desde otro usuario se rechaza (422).</li>
 * </ul>
 */
@Service
public class TransaccionService {

    /** Resultado de una operación; {@code repetida} indica que se devolvió la transacción original de la clave. */
    public record Resultado(TransaccionResponse transaccion, boolean repetida) {
    }

    static final BigDecimal LIMITE_POR_OPERACION = new BigDecimal("50000.00");
    static final int MAX_LONGITUD_CLAVE = 64;
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private final TransaccionRepository repository;
    private final CuentaRepository cuentas;
    private final ClienteRepository clientes;
    private final AuditoriaService auditoria;

    public TransaccionService(TransaccionRepository repository, CuentaRepository cuentas,
            ClienteRepository clientes, AuditoriaService auditoria) {
        this.repository = repository;
        this.cuentas = cuentas;
        this.clientes = clientes;
        this.auditoria = auditoria;
    }

    @Transactional
    public Resultado depositar(OperacionRequest req, String claveIdempotencia, String usuario) {
        String clave = normalizar(claveIdempotencia);
        Optional<Resultado> repeticion = repeticion(clave, usuario, Transaccion.Tipo.DEPOSITO, null, req.clabe(), req.monto());
        if (repeticion.isPresent()) {
            return repeticion.get();
        }
        validarMonto(req.monto());
        Cuenta cuenta = bloquear(idDe(req.clabe()));
        cuenta.abonar(req.monto());
        return registrar(new Transaccion(Transaccion.Tipo.DEPOSITO, null, cuenta, req.monto(), req.concepto(),
                clave, usuario));
    }

    @Transactional
    public Resultado retirar(OperacionRequest req, String claveIdempotencia, String usuario) {
        String clave = normalizar(claveIdempotencia);
        Optional<Resultado> repeticion = repeticion(clave, usuario, Transaccion.Tipo.RETIRO, req.clabe(), null, req.monto());
        if (repeticion.isPresent()) {
            return repeticion.get();
        }
        validarMonto(req.monto());
        Cuenta cuenta = bloquear(idDe(req.clabe()));
        cuenta.cargar(req.monto());
        return registrar(new Transaccion(Transaccion.Tipo.RETIRO, cuenta, null, req.monto(), req.concepto(),
                clave, usuario));
    }

    @Transactional
    public Resultado transferir(TransferenciaRequest req, String claveIdempotencia, String usuario) {
        String clave = normalizar(claveIdempotencia);
        Optional<Resultado> repeticion = repeticion(clave, usuario, Transaccion.Tipo.TRANSFERENCIA,
                req.clabeOrigen(), req.clabeDestino(), req.monto());
        if (repeticion.isPresent()) {
            return repeticion.get();
        }
        if (req.clabeOrigen().equals(req.clabeDestino())) {
            throw new NegocioException("MISMA_CUENTA", "La cuenta origen y destino no pueden ser la misma");
        }
        validarMonto(req.monto());

        Long idOrigen = idDe(req.clabeOrigen());
        Long idDestino = idDe(req.clabeDestino());

        // Orden determinista de bloqueo: primero el id menor
        Cuenta primera = bloquear(Math.min(idOrigen, idDestino));
        Cuenta segunda = bloquear(Math.max(idOrigen, idDestino));
        Cuenta origen = primera.getId().equals(idOrigen) ? primera : segunda;
        Cuenta destino = origen == primera ? segunda : primera;

        validarKyc(origen);
        validarKyc(destino);
        origen.cargar(req.monto());
        destino.abonar(req.monto());

        return registrar(new Transaccion(Transaccion.Tipo.TRANSFERENCIA, origen, destino, req.monto(),
                req.concepto(), clave, usuario));
    }

    @Transactional(readOnly = true)
    public TransaccionResponse obtener(String folio) {
        return repository.findByFolio(folio).map(TransaccionResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Transacción", folio));
    }

    @Transactional(readOnly = true)
    public Page<TransaccionResponse> movimientos(Long cuentaId, Pageable pageable) {
        return repository.movimientos(cuentaId, pageable).map(TransaccionResponse::de);
    }

    @Transactional(readOnly = true)
    public Tablero tablero() {
        var inicioDia = LocalDate.now(ZONA).atStartOfDay(ZONA).toInstant();
        return new Tablero(
                clientes.count(),
                clientes.countByEstadoKyc(EstadoKyc.PENDIENTE),
                clientes.countByEstadoKyc(EstadoKyc.VERIFICADO),
                clientes.countByEstadoKyc(EstadoKyc.RECHAZADO),
                cuentas.count(),
                cuentas.saldoTotal(),
                repository.countByCreadoEnGreaterThanEqual(inicioDia),
                repository.volumenDesde(inicioDia));
    }

    private Resultado registrar(Transaccion t) {
        Transaccion guardada;
        try {
            guardada = repository.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones simultáneas con la misma clave: la otra ganó el INSERT
            throw new ConflictoException("IDEMPOTENCIA_EN_CURSO",
                    "Ya se está procesando una operación con esta Idempotency-Key; consulta el resultado en unos segundos");
        }
        auditoria.registrar(t.getRealizadaPor(), t.getTipo().name(), "TRANSACCION", guardada.getFolio(),
                "Monto " + t.getMonto());
        return new Resultado(TransaccionResponse.de(guardada), false);
    }

    /**
     * Si la clave ya se usó, devuelve la transacción original siempre que la petición sea la misma
     * (tipo, cuentas, monto y usuario). Con datos distintos la clave se está reutilizando por error.
     */
    private Optional<Resultado> repeticion(String clave, String usuario, Transaccion.Tipo tipo, String clabeOrigen,
            String clabeDestino, BigDecimal monto) {
        if (clave == null) {
            return Optional.empty();
        }
        return repository.findByClaveIdempotencia(clave).map(t -> {
            TransaccionResponse original = TransaccionResponse.de(t);
            boolean misma = t.getTipo() == tipo
                    && t.getRealizadaPor().equals(usuario)
                    && Objects.equals(original.clabeOrigen(), clabeOrigen)
                    && Objects.equals(original.clabeDestino(), clabeDestino)
                    && t.getMonto().compareTo(monto) == 0;
            if (!misma) {
                throw new NegocioException("IDEMPOTENCIA_REUTILIZADA",
                        "La Idempotency-Key ya se usó para otra operación; genera una clave nueva");
            }
            return new Resultado(original, true);
        });
    }

    private static String normalizar(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        if (clave.length() > MAX_LONGITUD_CLAVE) {
            throw new IllegalArgumentException("Idempotency-Key admite máximo " + MAX_LONGITUD_CLAVE + " caracteres");
        }
        return clave.trim();
    }

    private Long idDe(String clabe) {
        return cuentas.findIdByClabe(clabe)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", clabe));
    }

    private Cuenta bloquear(Long id) {
        return cuentas.findByIdParaActualizar(id).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta", id));
    }

    private static void validarMonto(BigDecimal monto) {
        if (monto.compareTo(LIMITE_POR_OPERACION) > 0) {
            throw new NegocioException("LIMITE_EXCEDIDO",
                    "El monto excede el límite por operación de $" + LIMITE_POR_OPERACION);
        }
    }

    private static void validarKyc(Cuenta cuenta) {
        if (cuenta.getCliente().getEstadoKyc() != EstadoKyc.VERIFICADO) {
            throw new NegocioException("KYC_REQUERIDO", "El titular de la cuenta " + cuenta.getClabe()
                    + " no tiene la identidad verificada");
        }
    }
}
