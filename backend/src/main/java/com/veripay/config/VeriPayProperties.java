package com.veripay.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "veripay")
public record VeriPayProperties(
        Jwt jwt,
        Biometria biometria,
        Banco banco,
        Cors cors,
        Admin admin,
        @DefaultValue("false") boolean demoDatos) {

    public record Jwt(String secreto, @DefaultValue("120") long expiracionMinutos) {
    }

    /**
     * @param motor  "facial" (OpenCV YuNet + SFace) o "perceptual" (hashes de imagen, solo para pruebas)
     * @param umbral opcional; si se omite se usa el recomendado por el motor
     */
    public record Biometria(@DefaultValue("facial") String motor, BigDecimal umbral) {
    }

    public record Banco(String claveBanco, String clavePlaza) {
    }

    public record Cors(List<String> origenes) {
    }

    public record Admin(String username, String password) {
    }
}
