package com.veripay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Punto de entrada. Extiende {@link SpringBootServletInitializer} para que el mismo
 * WAR pueda desplegarse en JBoss EAP / WildFly o ejecutarse standalone con Tomcat embebido.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VeriPayApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(VeriPayApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(VeriPayApplication.class);
    }
}
