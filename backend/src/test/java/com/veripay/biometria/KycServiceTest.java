package com.veripay.biometria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.veripay.cliente.ClienteDtos.AltaClienteRequest;
import com.veripay.cliente.ClienteService;
import com.veripay.cliente.Curp;
import com.veripay.cliente.EstadoKyc;
import com.veripay.common.ConflictoException;
import com.veripay.common.NegocioException;

@SpringBootTest
@ActiveProfiles("test")
class KycServiceTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(80);

    @Autowired KycService kyc;
    @Autowired ClienteService clientes;

    private Long clienteId;

    @BeforeEach
    void clientePendiente() {
        int n = SECUENCIA.incrementAndGet();
        String curp17 = String.format("VAGL83%02d%02dMNLRRS0", (n / 28) % 12 + 1, n % 28 + 1);
        String curp = curp17 + Curp.digitoVerificador(curp17);
        clienteId = clientes.alta(new AltaClienteRequest(curp, null, "Lucía", "Vargas", null,
                Curp.fechaNacimiento(curp).orElseThrow(), "lucia" + n + "@test.mx", null), "test").getId();
    }

    @Test
    void mismoArchivoDosVecesNoApruebaElKyc() throws Exception {
        byte[] foto = Imagenes.rostro(200, 260, false);

        assertThatThrownBy(() -> kyc.verificar(clienteId, foto, foto.clone(), "test"))
                .isInstanceOf(NegocioException.class)
                .extracting("codigo").isEqualTo("IMAGENES_IDENTICAS");
        assertThat(clientes.obtener(clienteId).getEstadoKyc()).isEqualTo(EstadoKyc.PENDIENTE);
    }

    @Test
    void mismaFotoReescaladaSiApruebaYNoPermiteVerificarDosVeces() throws Exception {
        VerificacionBiometrica v = kyc.verificar(clienteId, Imagenes.rostro(200, 260, false),
                Imagenes.rostro(120, 156, false), "test");

        assertThat(v.isAprobada()).isTrue();
        assertThat(clientes.obtener(clienteId).getEstadoKyc()).isEqualTo(EstadoKyc.VERIFICADO);
        assertThatThrownBy(() -> kyc.verificar(clienteId, Imagenes.rostro(200, 260, false),
                Imagenes.rostro(100, 130, false), "test"))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void tresRechazosBloqueanNuevosIntentos() throws Exception {
        byte[] identificacion = Imagenes.rostro(200, 260, false);
        for (int i = 0; i < KycService.MAX_RECHAZOS; i++) {
            byte[] otraPersona = Imagenes.rostro(200 + i, 260, true);
            assertThat(kyc.verificar(clienteId, identificacion, otraPersona, "test").isAprobada()).isFalse();
        }

        assertThatThrownBy(() -> kyc.verificar(clienteId, identificacion, Imagenes.rostro(210, 260, true), "test"))
                .isInstanceOf(NegocioException.class)
                .extracting("codigo").isEqualTo("KYC_INTENTOS_AGOTADOS");
        assertThat(clientes.obtener(clienteId).getEstadoKyc()).isEqualTo(EstadoKyc.RECHAZADO);
    }
}
