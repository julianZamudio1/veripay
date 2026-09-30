package com.veripay;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import com.veripay.cliente.ClienteDtos.AltaClienteRequest;
import com.veripay.cliente.ClienteRepository;
import com.veripay.cliente.ClienteService;
import com.veripay.cliente.Curp;
import com.veripay.cliente.EstadoKyc;
import com.veripay.cuenta.CuentaService;
import com.veripay.transaccion.TransaccionDtos.OperacionRequest;
import com.veripay.transaccion.TransaccionService;

/**
 * Contrato HTTP del API: códigos de estado, cabeceras (Location, WWW-Authenticate,
 * Idempotent-Replayed) y formato de error RFC 9457.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiRestTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(40);
    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired MockMvc mvc;
    @Autowired ClienteService clientes;
    @Autowired ClienteRepository clienteRepository;
    @Autowired CuentaService cuentas;
    @Autowired TransaccionService transacciones;
    @Autowired TransactionTemplate tx;

    private Long cuentaId;
    private String clabe;

    @BeforeEach
    void cuentaConSaldo() {
        int n = SECUENCIA.incrementAndGet();
        String curp17 = String.format("RELA80%02d%02dMDFSRR0", (n / 28) % 12 + 1, n % 28 + 1);
        String curp = curp17 + Curp.digitoVerificador(curp17);
        Long clienteId = clientes.alta(new AltaClienteRequest(curp, null, "Laura", "Reyes", null,
                Curp.fechaNacimiento(curp).orElseThrow(), "laura" + n + "@test.mx", null), "test").getId();
        tx.executeWithoutResult(s -> clienteRepository.findById(clienteId).orElseThrow().marcarKyc(EstadoKyc.VERIFICADO));
        var cuenta = cuentas.abrir(clienteId, "test");
        cuentaId = cuenta.id();
        clabe = cuenta.clabe();
        transacciones.depositar(new OperacionRequest(clabe, new BigDecimal("500.00"), null), null, "test");
    }

    // ---- Errores del cliente: 4xx con application/problem+json, nunca 500

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        mvc.perform(post("/api/v1/clientes").with(analista()).contentType(MediaType.APPLICATION_JSON).content("{\"curp\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.codigo").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.instance").value("/api/v1/clientes"));
    }

    @Test
    void enumInvalidoEnQueryResponde400() throws Exception {
        mvc.perform(get("/api/v1/clientes?estado=FOO").with(analista()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void idNoNumericoResponde400() throws Exception {
        mvc.perform(get("/api/v1/clientes/abc").with(analista()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tamanoDePaginaFueraDeRangoResponde400() throws Exception {
        mvc.perform(get("/api/v1/clientes?tamano=500").with(analista()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rutaInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/v1/nada").with(analista()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOT_FOUND"));
    }

    @Test
    void sinTokenResponde401ConCuerpoYCabeceraBearer() throws Exception {
        mvc.perform(get("/api/v1/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void metodoNoPermitidoResponde405() throws Exception {
        mvc.perform(delete("/api/v1/clientes/1").with(analista()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    // ---- Creación: 201 + Location que apunta a un recurso consultable

    @Test
    void altaDeClienteDevuelveLocation() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String curp17 = String.format("MOLP77%02d%02dHJCRRS0", (n / 28) % 12 + 1, n % 28 + 1);
        String curp = curp17 + Curp.digitoVerificador(curp17);
        String json = """
                {"curp":"%s","nombre":"Pedro","apellidoPaterno":"Mora","fechaNacimiento":"%s","email":"pedro%d@test.mx"}
                """.formatted(curp, Curp.fechaNacimiento(curp).orElseThrow(), n);

        String location = mvc.perform(post("/api/v1/clientes").with(analista())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/clientes/")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location).with(analista())).andExpect(status().isOk())
                .andExpect(jsonPath("$.curp").value(curp));

        // Repetir el alta: conflicto con el recurso existente
        mvc.perform(post("/api/v1/clientes").with(analista()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CURP_DUPLICADA"));
    }

    @Test
    void depositoDevuelveLocationYReintentoMarcaIdempotentReplayed() throws Exception {
        String cuerpo = "{\"clabe\":\"" + clabe + "\",\"monto\":25.50}";
        String clave = "api-" + SECUENCIA.incrementAndGet();

        String location = mvc.perform(post("/api/v1/transacciones/depositos").with(analista())
                        .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post("/api/v1/transacciones/depositos").with(analista())
                        .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().string("Location", location));

        mvc.perform(get(location).with(analista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("DEPOSITO"));
        mvc.perform(get("/api/v1/cuentas/" + cuentaId).with(analista()))
                .andExpect(jsonPath("$.saldo").value(525.50));
    }

    // ---- Recursos y verbos

    @Test
    void patchCambiaEstadoDeCuentaYEsIdempotente() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/v1/cuentas/" + cuentaId).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"BLOQUEADA\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("BLOQUEADA"));
        }
        mvc.perform(post("/api/v1/transacciones/depositos").with(analista())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clabe\":\"" + clabe + "\",\"monto\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
    }

    @Test
    void analistaNoPuedeBloquearCuentas() throws Exception {
        mvc.perform(patch("/api/v1/cuentas/" + cuentaId).with(analista())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"BLOQUEADA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void listadoDeCuentasPaginadoYFiltradoPorClabe() throws Exception {
        mvc.perform(get("/api/v1/cuentas?clabe=" + clabe).with(analista()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(cuentaId));
    }

    @Test
    void aperturaDeCuentaAnidadaEnCliente() throws Exception {
        Long clienteId = cuentas.obtener(cuentaId).clienteId();
        mvc.perform(post("/api/v1/clientes/" + clienteId + "/cuentas").with(analista()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(header().string("Location", containsString("/api/v1/cuentas/")))
                .andExpect(jsonPath("$.clabe", notNullValue()));
    }

    @Test
    void loginFallidoQuedaEnAuditoria() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruso\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auditoria?tamano=5").with(admin()))
                .andExpect(jsonPath("$.contenido[0].accion").value("LOGIN_FALLIDO"))
                .andExpect(jsonPath("$.contenido[0].usuario").value("intruso"))
                .andExpect(jsonPath("$.contenido[0].entidad", endsWith("USUARIO")));
    }

    private static RequestPostProcessor analista() {
        return jwt().jwt(j -> j.subject("analista")).authorities(new SimpleGrantedAuthority("ROLE_ANALISTA"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
