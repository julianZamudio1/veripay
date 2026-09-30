package com.veripay.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

    @Transactional(readOnly = true)
    public Page<EventoAuditoria> listar(Pageable pageable) {
        return repository.findAllByOrderByCreadoEnDescIdDesc(pageable);
    }
}
