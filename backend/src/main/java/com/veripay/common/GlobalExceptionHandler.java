package com.veripay.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce excepciones a respuestas RFC 9457. Al extender {@link ResponseEntityExceptionHandler}
 * los errores propios de Spring MVC (JSON mal formado, tipo de parámetro inválido, método no
 * permitido, archivo demasiado grande…) responden 4xx en lugar de caer en el 500 genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Errores de Spring MVC (heredados): se completan con codigo, type, instance y timestamp

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = body instanceof ProblemDetail p ? p
                : ProblemDetail.forStatusAndDetail(status, "La solicitud no pudo procesarse");
        if (ex instanceof NoResourceFoundException) {
            problema.setDetail("El recurso solicitado no existe");
        } else if (ex instanceof HttpMessageNotReadableException) {
            problema.setDetail("El cuerpo de la solicitud no es JSON válido o tiene un tipo de dato incorrecto");
        } else if (ex instanceof TypeMismatchException tm) {
            String nombre = tm instanceof MethodArgumentTypeMismatchException m ? m.getName() : tm.getPropertyName();
            problema.setDetail("El parámetro '" + nombre + "' tiene un valor inválido: '" + tm.getValue() + "'");
        } else if (ex instanceof MaxUploadSizeExceededException) {
            problema.setDetail("El archivo supera el tamaño máximo permitido (5 MB por archivo)");
        } else if (ex instanceof HandlerMethodValidationException) {
            problema.setDetail("Uno o más parámetros están fuera del rango permitido");
        }
        if (problema.getProperties() == null || !problema.getProperties().containsKey("codigo")) {
            Problemas.completar(problema, Problemas.codigoPara(status), ruta(request));
        }
        return super.handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail p = Problemas.crear(status, "VALIDACION", "La solicitud contiene datos inválidos", ruta(request));
        p.setTitle("Datos inválidos");
        p.setProperty("campos", campos);
        return super.handleExceptionInternal(ex, p, headers, status, request);
    }

    // ---- Errores de la aplicación

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail negocio(NegocioException ex, WebRequest request) {
        return Problemas.crear(ex.getStatus(), ex.getCodigo(), ex.getMessage(), ruta(request));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex, WebRequest request) {
        return Problemas.crear(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage(), ruta(request));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail autenticacion(AuthenticationException ex, WebRequest request) {
        return Problemas.crear(HttpStatus.UNAUTHORIZED, "CREDENCIALES", "Usuario o contraseña incorrectos", ruta(request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException ex, WebRequest request) {
        return Problemas.crear(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tienes permiso para esta operación",
                ruta(request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex, WebRequest request) {
        return Problemas.crear(HttpStatus.CONFLICT, "DUPLICADO",
                "El registro ya existe o viola una restricción de la base de datos", ruta(request));
    }

    /** Bloqueo optimista (@Version) o pesimista que no pudo obtenerse: otra operación modificó el recurso. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail concurrencia(ConcurrencyFailureException ex, WebRequest request) {
        return Problemas.crear(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "Otra operación modificó el recurso al mismo tiempo; vuelve a intentarlo", ruta(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail argumento(IllegalArgumentException ex, WebRequest request) {
        return Problemas.crear(HttpStatus.BAD_REQUEST, "ARGUMENTO_INVALIDO", ex.getMessage(), ruta(request));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail general(Exception ex, WebRequest request) {
        log.error("Error no controlado en {}", ruta(request), ex);
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado",
                ruta(request));
    }

    private static String ruta(WebRequest request) {
        return request instanceof ServletWebRequest s ? s.getRequest().getRequestURI() : null;
    }
}
