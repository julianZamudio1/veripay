package com.veripay.common;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Construye respuestas de error con el formato RFC 9457 (application/problem+json).
 * Además de los campos estándar (type, title, status, detail, instance) cada problema lleva
 * {@code codigo}, estable y pensado para que el frontend decida qué hacer, y {@code timestamp}.
 */
public final class Problemas {

    private Problemas() {
    }

    public static ProblemDetail crear(HttpStatusCode status, String codigo, String detalle, String instancia) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalle);
        completar(p, codigo, instancia);
        return p;
    }

    /** Agrega codigo, type, instance y timestamp a un ProblemDetail ya existente. */
    public static void completar(ProblemDetail p, String codigo, String instancia) {
        p.setType(URI.create("urn:veripay:problema:" + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
        if (instancia != null) {
            p.setInstance(URI.create(instancia));
        }
        p.setProperty("codigo", codigo);
        p.setProperty("timestamp", Instant.now());
    }

    /** Código por defecto para errores del framework: el nombre del estado HTTP (NOT_FOUND, BAD_REQUEST…). */
    public static String codigoPara(HttpStatusCode status) {
        HttpStatus s = HttpStatus.resolve(status.value());
        return s != null ? s.name() : "HTTP_" + status.value();
    }
}
