package com.turismo.common;

/**
 * Base para "la petición es válida pero rompe una regla de negocio"
 * (sin cupo, clima desfavorable, reserva ya cancelada...). Se traduce a HTTP 409 (Conflict).
 * Los servicios crean subclases (CupoAgotadoException, CupoInsuficienteException).
 */
public class ConflictoNegocioException extends RuntimeException {
    public ConflictoNegocioException(String mensaje) { super(mensaje); }
}
