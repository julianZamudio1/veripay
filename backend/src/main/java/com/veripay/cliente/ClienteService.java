package com.veripay.cliente;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.auditoria.AuditoriaService;
import com.veripay.cliente.ClienteDtos.AltaClienteRequest;
import com.veripay.cliente.ClienteDtos.ContactoRequest;
import com.veripay.common.ConflictoException;
import com.veripay.common.NegocioException;
import com.veripay.common.RecursoNoEncontradoException;

@Service
public class ClienteService {

    static final int EDAD_MINIMA = 18;

    private final ClienteRepository repository;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional
    public Cliente alta(AltaClienteRequest req, String usuario) {
        String curp = req.curp().trim().toUpperCase();
        String email = req.email().trim().toLowerCase();

        LocalDate fechaCurp = Curp.fechaNacimiento(curp)
                .orElseThrow(() -> new NegocioException("CURP_INVALIDA", "La CURP no contiene una fecha válida"));
        if (!fechaCurp.equals(req.fechaNacimiento())) {
            throw new NegocioException("CURP_FECHA", "La fecha de nacimiento no coincide con la CURP (" + fechaCurp + ")");
        }
        if (Period.between(req.fechaNacimiento(), LocalDate.now()).getYears() < EDAD_MINIMA) {
            throw new NegocioException("MENOR_DE_EDAD", "El cliente debe ser mayor de edad");
        }
        if (repository.existsByCurp(curp)) {
            throw new ConflictoException("CURP_DUPLICADA", "Ya existe un cliente con la CURP " + curp);
        }
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictoException("EMAIL_DUPLICADO", "Ya existe un cliente con el correo " + email);
        }

        Cliente cliente = repository.save(new Cliente(curp, vacioANulo(req.rfc()), req.nombre().trim(),
                req.apellidoPaterno().trim(), vacioANulo(req.apellidoMaterno()), req.fechaNacimiento(), email,
                vacioANulo(req.telefono())));
        auditoria.registrar(usuario, "ALTA_CLIENTE", "CLIENTE", cliente.getId(), "CURP " + curp);
        return cliente;
    }

    @Transactional
    public Cliente actualizarContacto(Long id, ContactoRequest req, String usuario) {
        Cliente cliente = obtener(id);
        String email = req.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(cliente.getEmail()) && repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictoException("EMAIL_DUPLICADO", "Ya existe un cliente con el correo " + email);
        }
        cliente.actualizarContacto(email, vacioANulo(req.telefono()));
        auditoria.registrar(usuario, "ACTUALIZA_CONTACTO", "CLIENTE", id, null);
        return cliente;
    }

    @Transactional(readOnly = true)
    public Cliente obtener(Long id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    /** Obtiene el cliente con bloqueo de escritura; debe llamarse dentro de una transacción. */
    @Transactional
    public Cliente obtenerParaActualizar(Long id) {
        return repository.findByIdParaActualizar(id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    @Transactional(readOnly = true)
    public Page<Cliente> buscar(String texto, EstadoKyc estado, Pageable pageable) {
        String patron = texto == null ? "" : texto.trim()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return repository.buscar(patron, estado, pageable);
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
