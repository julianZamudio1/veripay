package com.veripay.biometria;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.auditoria.AuditoriaService;
import com.veripay.cliente.Cliente;
import com.veripay.cliente.ClienteService;
import com.veripay.cliente.EstadoKyc;
import com.veripay.common.NegocioException;
import com.veripay.config.VeriPayProperties;

@Service
public class KycService {

    private final ClienteService clientes;
    private final ComparadorBiometrico comparador;
    private final VerificacionBiometricaRepository repository;
    private final AuditoriaService auditoria;
    private final BigDecimal umbral;

    public KycService(ClienteService clientes, ComparadorBiometrico comparador,
            VerificacionBiometricaRepository repository, AuditoriaService auditoria, VeriPayProperties props) {
        this.clientes = clientes;
        this.comparador = comparador;
        this.repository = repository;
        this.auditoria = auditoria;
        this.umbral = props.biometria().umbral();
    }

    @Transactional
    public VerificacionBiometrica verificar(Long clienteId, byte[] identificacion, byte[] selfie, String usuario) {
        Cliente cliente = clientes.obtener(clienteId);
        if (cliente.getEstadoKyc() == EstadoKyc.VERIFICADO) {
            throw new NegocioException("KYC_YA_VERIFICADO", "El cliente ya tiene su identidad verificada");
        }

        BigDecimal puntaje = comparador.comparar(identificacion, selfie);
        VerificacionBiometrica v = repository.save(new VerificacionBiometrica(
                cliente, puntaje, umbral, sha256(identificacion), sha256(selfie), usuario));

        cliente.marcarKyc(v.isAprobada() ? EstadoKyc.VERIFICADO : EstadoKyc.RECHAZADO);
        auditoria.registrar(usuario, v.isAprobada() ? "KYC_APROBADO" : "KYC_RECHAZADO", "CLIENTE", clienteId,
                "Puntaje " + puntaje + " / umbral " + umbral);
        return v;
    }

    @Transactional(readOnly = true)
    public List<VerificacionBiometrica> historial(Long clienteId) {
        clientes.obtener(clienteId);
        return repository.findByClienteIdOrderByCreadoEnDesc(clienteId);
    }

    private static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
