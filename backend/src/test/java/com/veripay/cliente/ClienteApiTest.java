package com.veripay.cliente;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClienteApiTest {

    @Autowired MockMvc mvc;

    private static String cuerpo(String curp17, String fecha, String email) {
        String curp = curp17 + Curp.digitoVerificador(curp17);
        return """
                {"curp":"%s","nombre":"María","apellidoPaterno":"Sánchez","fechaNacimiento":"%s",
                 "email":"%s","telefono":"5500000000"}
                """.formatted(curp, fecha, email);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginConAdminEmiteToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void loginConPasswordIncorrectoResponde401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"otra\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES"));
    }

    @Test
    void altaValidaQuedaPendienteDeKyc() throws Exception {
        mvc.perform(post("/api/clientes").with(analista()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("SAMM950610MDFNRR0", "1995-06-10", "maria.api@test.mx")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoKyc").value("PENDIENTE"))
                .andExpect(jsonPath("$.nombreCompleto").value("María Sánchez"));
    }

    @Test
    void curpConDigitoIncorrectoResponde400ConDetalleDeCampo() throws Exception {
        int digitoErroneo = (Curp.digitoVerificador("SAMM950610MDFNRR0") + 1) % 10;
        String json = """
                {"curp":"SAMM950610MDFNRR0%d","nombre":"María","apellidoPaterno":"Sánchez",
                 "fechaNacimiento":"1995-06-10","email":"x@test.mx"}
                """.formatted(digitoErroneo);
        mvc.perform(post("/api/clientes").with(analista()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.curp", notNullValue()));
    }

    @Test
    void fechaQueNoCoincideConCurpResponde422() throws Exception {
        mvc.perform(post("/api/clientes").with(analista()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("SAMM950611MDFNRR0", "1995-06-12", "otra.fecha@test.mx")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CURP_FECHA"));
    }

    @Test
    void auditorNoPuedeDarDeAlta() throws Exception {
        mvc.perform(post("/api/clientes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("SAMM950612MDFNRR0", "1995-06-12", "auditor@test.mx")))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor analista() {
        return jwt().jwt(j -> j.subject("analista")).authorities(new SimpleGrantedAuthority("ROLE_ANALISTA"));
    }
}
