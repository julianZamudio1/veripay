package com.veripay.cuenta;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    boolean existsByClabe(String clabe);

    long countByClienteId(Long clienteId);

    @Query("select c from Cuenta c join fetch c.cliente where c.clabe = :clabe")
    Optional<Cuenta> findByClabe(@Param("clabe") String clabe);

    /**
     * Solo el id: si se cargara la entidad antes del bloqueo, Hibernate reutilizaría esa copia
     * (sin refrescar) al hacer el SELECT ... FOR UPDATE y el saldo estaría desactualizado.
     */
    @Query("select c.id from Cuenta c where c.clabe = :clabe")
    Optional<Long> findIdByClabe(@Param("clabe") String clabe);

    @Query("select c from Cuenta c join fetch c.cliente where c.cliente.id = :clienteId order by c.id")
    List<Cuenta> findByClienteId(@Param("clienteId") Long clienteId);

    @Query("select c from Cuenta c join fetch c.cliente order by c.id desc")
    List<Cuenta> findAllConCliente();

    /** Bloqueo pesimista (SELECT ... FOR UPDATE) para operaciones de saldo concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id")
    Optional<Cuenta> findByIdParaActualizar(@Param("id") Long id);

    @Query("select coalesce(sum(c.saldo), 0) from Cuenta c")
    BigDecimal saldoTotal();
}
