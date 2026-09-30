package com.veripay.biometria;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacionBiometricaRepository extends JpaRepository<VerificacionBiometrica, Long> {

    List<VerificacionBiometrica> findByClienteIdOrderByCreadoEnDesc(Long clienteId);
}
