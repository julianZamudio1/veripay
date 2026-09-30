package com.veripay.transaccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.veripay.cliente.Cliente;
import com.veripay.cliente.ClienteDtos.AltaClienteRequest;
import com.veripay.cliente.ClienteRepository;
import com.veripay.cliente.ClienteService;
import com.veripay.cliente.Curp;
import com.veripay.cliente.EstadoKyc;
import com.veripay.common.NegocioException;
import com.veripay.cuenta.CuentaRepository;
import com.veripay.cuenta.CuentaService;
import com.veripay.transaccion.TransaccionDtos.OperacionRequest;
import com.veripay.transaccion.TransaccionDtos.TransaccionResponse;
import com.veripay.transaccion.TransaccionDtos.TransferenciaRequest;

@SpringBootTest
@ActiveProfiles("test")
class TransaccionServiceTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired TransaccionService service;
    @Autowired ClienteService clientes;
    @Autowired ClienteRepository clienteRepository;
    @Autowired CuentaService cuentas;
    @Autowired CuentaRepository cuentaRepository;
    @Autowired TransactionTemplate tx;

    private String clabeA;
    private String clabeB;

    @BeforeEach
    void preparar() {
        clabeA = cuentaVerificadaConSaldo("1000.00");
        clabeB = cuentaVerificadaConSaldo("0.01");
    }

    @Test
    void transferenciaMueveElDinero() {
        TransaccionResponse r = service.transferir(
                new TransferenciaRequest(clabeA, clabeB, new BigDecimal("250.00"), "Prueba"), null, "test");

        assertThat(r.tipo()).isEqualTo(Transaccion.Tipo.TRANSFERENCIA);
        assertThat(saldo(clabeA)).isEqualByComparingTo("750.00");
        assertThat(saldo(clabeB)).isEqualByComparingTo("250.01");
    }

    @Test
    void saldoInsuficienteNoAlteraNinguna() {
        assertThatThrownBy(() -> service.transferir(
                new TransferenciaRequest(clabeA, clabeB, new BigDecimal("1000.01"), null), null, "test"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Saldo insuficiente");

        assertThat(saldo(clabeA)).isEqualByComparingTo("1000.00");
        assertThat(saldo(clabeB)).isEqualByComparingTo("0.01");
    }

    @Test
    void reintentoConMismaClaveNoDuplicaElCargo() {
        var req = new TransferenciaRequest(clabeA, clabeB, new BigDecimal("100.00"), null);
        String clave = "reintento-" + SECUENCIA.incrementAndGet();

        TransaccionResponse primera = service.transferir(req, clave, "test");
        TransaccionResponse segunda = service.transferir(req, clave, "test");

        assertThat(segunda.folio()).isEqualTo(primera.folio());
        assertThat(saldo(clabeA)).isEqualByComparingTo("900.00");
    }

    @Test
    void respetaLimitePorOperacion() {
        assertThatThrownBy(() -> service.depositar(
                new OperacionRequest(clabeA, new BigDecimal("50000.01"), null), null, "test"))
                .isInstanceOf(NegocioException.class)
                .extracting("codigo").isEqualTo("LIMITE_EXCEDIDO");
    }

    @Test
    void transferenciasCruzadasConcurrentesConservanElTotal() throws Exception {
        int hilos = 8;
        int porHilo = 10;
        BigDecimal totalInicial = saldo(clabeA).add(saldo(clabeB));
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> tareas = new ArrayList<>();

        for (int h = 0; h < hilos; h++) {
            boolean deAaB = h % 2 == 0;
            tareas.add(pool.submit(() -> {
                salida.await();
                for (int i = 0; i < porHilo; i++) {
                    try {
                        service.transferir(new TransferenciaRequest(deAaB ? clabeA : clabeB, deAaB ? clabeB : clabeA,
                                new BigDecimal("5.00"), null), null, "test");
                    } catch (NegocioException saldoInsuficiente) {
                        // aceptable: B puede quedarse sin saldo momentáneamente
                    }
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> f : tareas) {
            f.get();
        }
        pool.shutdown();

        assertThat(saldo(clabeA).add(saldo(clabeB))).isEqualByComparingTo(totalInicial);
        assertThat(saldo(clabeA)).isNotNegative();
        assertThat(saldo(clabeB)).isNotNegative();
    }

    @Test
    void noPermiteAbrirCuentaSinKyc() {
        Cliente pendiente = nuevoCliente();
        assertThatThrownBy(() -> cuentas.abrir(pendiente.getId(), "test"))
                .isInstanceOf(NegocioException.class)
                .extracting("codigo").isEqualTo("KYC_REQUERIDO");
    }

    private String cuentaVerificadaConSaldo(String saldo) {
        Cliente c = nuevoCliente();
        tx.executeWithoutResult(s -> clienteRepository.findById(c.getId()).orElseThrow().marcarKyc(EstadoKyc.VERIFICADO));
        String clabe = cuentas.abrir(c.getId(), "test").clabe();
        service.depositar(new OperacionRequest(clabe, new BigDecimal(saldo), null), null, "test");
        return clabe;
    }

    private Cliente nuevoCliente() {
        int n = SECUENCIA.incrementAndGet();
        // Varía mes y día de nacimiento para generar CURPs distintas y válidas
        String curp17 = String.format("PEPJ90%02d%02dHDFRRN0", (n / 28) % 12 + 1, n % 28 + 1);
        String curp = curp17 + Curp.digitoVerificador(curp17);
        return clientes.alta(new AltaClienteRequest(curp, null, "Prueba", "Pérez", null,
                Curp.fechaNacimiento(curp).orElseThrow(), "prueba" + n + "@test.mx", null), "test");
    }

    private BigDecimal saldo(String clabe) {
        return cuentaRepository.findByClabe(clabe).orElseThrow().getSaldo();
    }
}
