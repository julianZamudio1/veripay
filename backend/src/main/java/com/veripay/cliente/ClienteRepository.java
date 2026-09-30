package com.veripay.cliente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    boolean existsByCurp(String curp);

    boolean existsByEmailIgnoreCase(String email);

    long countByEstadoKyc(EstadoKyc estado);

    @Query("""
            select c from Cliente c
            where (:estado is null or c.estadoKyc = :estado)
              and (lower(c.nombre) like lower(concat('%', :texto, '%'))
                   or lower(c.apellidoPaterno) like lower(concat('%', :texto, '%'))
                   or lower(c.curp) like lower(concat('%', :texto, '%'))
                   or lower(c.email) like lower(concat('%', :texto, '%')))
            """)
    Page<Cliente> buscar(@Param("texto") String texto, @Param("estado") EstadoKyc estado, Pageable pageable);
}
