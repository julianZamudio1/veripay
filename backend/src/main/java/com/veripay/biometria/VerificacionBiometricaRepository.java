package com.veripay.biometria;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacionBiometricaRepository extends JpaRepository<VerificacionBiometrica, Long> {

    List<VerificacionBiometrica> findByClienteIdOrderByCreadoEnDesc(Long clienteId);

    Optional<VerificacionBiometrica> findByIdAndClienteId(Long id, Long clienteId);

    long countByClienteIdAndAprobadaFalseAndCreadoEnAfter(Long clienteId, Instant desde);
}
