package com.guajiranet.migradoronu.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion;

/** Convierte los errores en Problem Details con un mensaje que la pantalla muestra tal cual (arquitectura 9.3). */
@RestControllerAdvice
public class ManejadorDeErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(ErrorDeNegocio.class)
    ProblemDetail negocio(ErrorDeNegocio error) {
        HttpStatus estado = switch (error.tipo()) {
            case DATOS_INVALIDOS -> HttpStatus.UNPROCESSABLE_CONTENT;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO -> HttpStatus.CONFLICT;
            case BLOQUEADO -> HttpStatus.LOCKED;
            case SIN_SESION -> HttpStatus.UNAUTHORIZED;
            case NO_DISPONIBLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ProblemDetail.forStatusAndDetail(estado, error.getMessage());
    }

    @ExceptionHandler(ErrorDeIntegracion.class)
    ProblemDetail integracion(ErrorDeIntegracion error) {
        LOG.warn("{}: {}", error.sistema(), error.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
    }

    /** Las reglas de los records (filtros incompatibles, SN vacío) llegan como IllegalArgumentException. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalido(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, error.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail noExiste(NoResourceFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Esa dirección no existe.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail ilegible(HttpMessageNotReadableException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La petición no tiene el formato esperado.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception error) {
        LOG.error("Error inesperado", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Intenta de nuevo y, si se repite, avisa al soporte.");
    }
}
