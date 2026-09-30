package com.veripay.cliente;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    boolean existsByCurp(String curp);

    boolean existsByEmailIgnoreCase(String email);

    long countByEstadoKyc(EstadoKyc estado);

    /**
     * SELECT ... FOR UPDATE sobre el cliente: serializa operaciones que dependen de su estado
     * (verificación KYC, límite de cuentas) cuando llegan al mismo tiempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> findByIdParaActualizar(@Param("id") Long id);

    /** {@code texto} debe llegar escapado (ver ClienteService): '!' escapa los comodines % y _. */
    @Query("""
            select c from Cliente c
            where (:estado is null or c.estadoKyc = :estado)
              and (lower(c.nombre) like lower(concat('%', :texto, '%')) escape '!'
                   or lower(c.apellidoPaterno) like lower(concat('%', :texto, '%')) escape '!'
                   or lower(c.curp) like lower(concat('%', :texto, '%')) escape '!'
                   or lower(c.email) like lower(concat('%', :texto, '%')) escape '!')
            """)
    Page<Cliente> buscar(@Param("texto") String texto, @Param("estado") EstadoKyc estado, Pageable pageable);
}
