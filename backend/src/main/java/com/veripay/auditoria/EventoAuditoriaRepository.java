package com.veripay.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    Page<EventoAuditoria> findAllByOrderByCreadoEnDescIdDesc(Pageable pageable);
}
