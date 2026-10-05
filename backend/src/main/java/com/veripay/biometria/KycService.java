package com.veripay.biometria;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.auditoria.AuditoriaService;
import com.veripay.cliente.Cliente;
import com.veripay.cliente.ClienteService;
import com.veripay.cliente.EstadoKyc;
import com.veripay.common.ConflictoException;
import com.veripay.common.NegocioException;
import com.veripay.common.RecursoNoEncontradoException;
import com.veripay.config.VeriPayProperties;

@Service
public class KycService {

    static final int MAX_RECHAZOS = 3;
    static final Duration VENTANA_INTENTOS = Duration.ofHours(24);

    private final ClienteService clientes;
    private final ComparadorBiometrico comparador;
    private final LectorDocumento lectorDocumento;
    private final VerificacionBiometricaRepository repository;
    private final AuditoriaService auditoria;
    private final BigDecimal umbral;

    public KycService(ClienteService clientes, ComparadorBiometrico comparador, LectorDocumento lectorDocumento,
            VerificacionBiometricaRepository repository, AuditoriaService auditoria, VeriPayProperties props) {
        this.lectorDocumento = lectorDocumento;
        this.clientes = clientes;
        this.comparador = comparador;
        this.repository = repository;
        this.auditoria = auditoria;
        BigDecimal configurado = props.biometria() == null ? null : props.biometria().umbral();
        this.umbral = configurado != null ? configurado : comparador.umbralRecomendado();
    }

    @Transactional
    public VerificacionBiometrica verificar(Long clienteId, byte[] identificacion, byte[] selfie, String usuario) {
        // Bloqueo: dos verificaciones simultáneas del mismo cliente se procesan una tras otra
        Cliente cliente = clientes.obtenerParaActualizar(clienteId);
        if (cliente.getEstadoKyc() == EstadoKyc.VERIFICADO) {
            throw new ConflictoException("KYC_YA_VERIFICADO", "El cliente ya tiene su identidad verificada");
        }
        long rechazosRecientes = repository.countByClienteIdAndAprobadaFalseAndCreadoEnAfter(
                clienteId, Instant.now().minus(VENTANA_INTENTOS));
        if (rechazosRecientes >= MAX_RECHAZOS) {
            throw new NegocioException("KYC_INTENTOS_AGOTADOS", "El cliente acumuló " + MAX_RECHAZOS
                    + " verificaciones rechazadas en 24 horas; debe esperar para volver a intentarlo");
        }

        String huellaIdentificacion = sha256(identificacion);
        String huellaSelfie = sha256(selfie);
        if (huellaIdentificacion.equals(huellaSelfie)) {
            // Sin esta regla, subir el mismo archivo dos veces daba similitud 1.0 y aprobaba el KYC
            throw new NegocioException("IMAGENES_IDENTICAS",
                    "La selfie y la identificación son el mismo archivo; se requieren dos fotografías distintas");
        }

        // Una foto ilegible no es un intento fallido: se pide otra sin contar el intento
        String curpIne = lectorDocumento.leerCurp(identificacion).orElseThrow(() -> new NegocioException(
                "CURP_ILEGIBLE", "No se pudo leer la CURP en la INE. Toma la foto de frente, completa, "
                        + "enfocada y sin reflejos sobre el texto."));

        BigDecimal puntaje = comparador.comparar(identificacion, selfie);
        VerificacionBiometrica v = repository.save(new VerificacionBiometrica(
                cliente, puntaje, umbral, curpIne, huellaIdentificacion, huellaSelfie, usuario));

        cliente.marcarKyc(v.isAprobada() ? EstadoKyc.VERIFICADO : EstadoKyc.RECHAZADO);
        auditoria.registrar(usuario, v.isAprobada() ? "KYC_APROBADO" : "KYC_RECHAZADO", "CLIENTE", clienteId,
                "Rostro " + puntaje + " / umbral " + umbral + "; CURP de la INE "
                        + (v.getCurpCoincide() ? "coincide" : "NO coincide (" + curpIne + ")"));
        return v;
    }

    @Transactional(readOnly = true)
    public VerificacionBiometrica obtener(Long clienteId, Long verificacionId) {
        return repository.findByIdAndClienteId(verificacionId, clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Verificación", verificacionId));
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
