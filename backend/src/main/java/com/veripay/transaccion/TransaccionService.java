package com.veripay.transaccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.auditoria.AuditoriaService;
import com.veripay.cliente.ClienteRepository;
import com.veripay.cliente.EstadoKyc;
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
 *   <li>La cabecera {@code Idempotency-Key} hace que un reintento del cliente no duplique el cargo.</li>
 * </ul>
 */
@Service
public class TransaccionService {

    static final BigDecimal LIMITE_POR_OPERACION = new BigDecimal("50000.00");
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
    public TransaccionResponse depositar(OperacionRequest req, String claveIdempotencia, String usuario) {
        Optional<Transaccion> previa = previa(claveIdempotencia);
        if (previa.isPresent()) {
            return TransaccionResponse.de(previa.get());
        }
        validarMonto(req.monto());
        Cuenta cuenta = bloquear(idDe(req.clabe()));
        cuenta.abonar(req.monto());
        return registrar(new Transaccion(Transaccion.Tipo.DEPOSITO, null, cuenta, req.monto(), req.concepto(),
                claveIdempotencia, usuario));
    }

    @Transactional
    public TransaccionResponse retirar(OperacionRequest req, String claveIdempotencia, String usuario) {
        Optional<Transaccion> previa = previa(claveIdempotencia);
        if (previa.isPresent()) {
            return TransaccionResponse.de(previa.get());
        }
        validarMonto(req.monto());
        Cuenta cuenta = bloquear(idDe(req.clabe()));
        cuenta.cargar(req.monto());
        return registrar(new Transaccion(Transaccion.Tipo.RETIRO, cuenta, null, req.monto(), req.concepto(),
                claveIdempotencia, usuario));
    }

    @Transactional
    public TransaccionResponse transferir(TransferenciaRequest req, String claveIdempotencia, String usuario) {
        Optional<Transaccion> previa = previa(claveIdempotencia);
        if (previa.isPresent()) {
            return TransaccionResponse.de(previa.get());
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
                req.concepto(), claveIdempotencia, usuario));
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

    private TransaccionResponse registrar(Transaccion t) {
        Transaccion guardada = repository.save(t);
        auditoria.registrar(t.getRealizadaPor(), t.getTipo().name(), "TRANSACCION", guardada.getFolio(),
                "Monto " + t.getMonto());
        return TransaccionResponse.de(guardada);
    }

    private Optional<Transaccion> previa(String claveIdempotencia) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank()) {
            return Optional.empty();
        }
        if (claveIdempotencia.length() > 64) {
            throw new IllegalArgumentException("Idempotency-Key admite máximo 64 caracteres");
        }
        return repository.findByClaveIdempotencia(claveIdempotencia);
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
