package com.turismo.common;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * Formato ÚNICO de error para todos los microservicios.
 * Cualquier cliente de la API recibe siempre el mismo JSON cuando algo falla:
 *   { "status": 404, "error": "Not Found", "mensaje": "No existe la actividad 7", "timestamp": "..." }
 * Es un "record": clase inmutable que solo guarda datos (sin getters/setters escritos a mano).
 */
public record ErrorResponse(int status, String error, String mensaje, LocalDateTime timestamp) {

    /** Atajo para armar el error a partir de un código HTTP y un mensaje. */
    public static ErrorResponse of(HttpStatus status, String mensaje) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), mensaje, LocalDateTime.now());
    }
}
