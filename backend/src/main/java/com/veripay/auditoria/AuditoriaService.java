package com.veripay.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository repository;

    public AuditoriaService(EventoAuditoriaRepository repository) {
        this.repository = repository;
    }

    /** Se une a la transacción del llamador: si la operación se revierte, su evento también. */
    @Transactional
    public void registrar(String usuario, String accion, String entidad, Object entidadId, String detalle) {
        String id = entidadId == null ? null : entidadId.toString();
        String det = detalle != null && detalle.length() > 500 ? detalle.substring(0, 500) : detalle;
        repository.save(new EventoAuditoria(usuario, accion, entidad, id, det));
    }

    /**
     * Registra en una transacción propia: el evento se conserva aunque la operación que lo
     * provoca termine en error (p. ej. un intento de login fallido).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAislado(String usuario, String accion, String entidad, Object entidadId, String detalle) {
        registrar(usuario, accion, entidad, entidadId, detalle);
    }

    @Transactional(readOnly = true)
    public Page<EventoAuditoria> listar(Pageable pageable) {
        return repository.findAllByOrderByCreadoEnDescIdDesc(pageable);
    }
}
