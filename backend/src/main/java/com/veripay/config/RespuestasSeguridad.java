package com.veripay.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veripay.common.Problemas;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Los 401 y 403 que genera el filtro de seguridad (antes de llegar a un controlador) responden con
 * el mismo formato RFC 9457 que el resto del API. Se conserva la cabecera WWW-Authenticate (RFC 6750).
 */
@Component
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerDenied = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper mapper;

    public RespuestasSeguridad(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        bearerEntryPoint.commence(request, response, ex);
        escribir(response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Falta el token de acceso o no es válido", request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        bearerDenied.handle(request, response, ex);
        escribir(response, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tienes permiso para esta operación",
                request.getRequestURI());
    }

    private void escribir(HttpServletResponse response, HttpStatus status, String codigo, String detalle,
            String ruta) throws IOException {
        ProblemDetail p = Problemas.crear(status, codigo, detalle, ruta);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), p);
    }
}
