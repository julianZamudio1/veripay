package com.veripay.transaccion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    Optional<Transaccion> findByClaveIdempotencia(String claveIdempotencia);

    @Query(value = """
            select t from Transaccion t
            left join fetch t.cuentaOrigen
            left join fetch t.cuentaDestino
            where (:cuentaId is null or t.cuentaOrigen.id = :cuentaId or t.cuentaDestino.id = :cuentaId)
            order by t.creadoEn desc, t.id desc
            """,
            countQuery = """
            select count(t) from Transaccion t
            where (:cuentaId is null or t.cuentaOrigen.id = :cuentaId or t.cuentaDestino.id = :cuentaId)
            """)
    Page<Transaccion> movimientos(@Param("cuentaId") Long cuentaId, Pageable pageable);

    long countByCreadoEnGreaterThanEqual(Instant desde);

    @Query("select coalesce(sum(t.monto), 0) from Transaccion t where t.creadoEn >= :desde")
    BigDecimal volumenDesde(@Param("desde") Instant desde);
}
