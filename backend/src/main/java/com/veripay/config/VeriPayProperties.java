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

    public record Biometria(@DefaultValue("0.80") BigDecimal umbral) {
    }

    public record Banco(String claveBanco, String clavePlaza) {
    }

    public record Cors(List<String> origenes) {
    }

    public record Admin(String username, String password) {
    }
}
