package com.turismo.common;

/**
 * Base para "no existe lo que pidieron" (actividad, reserva...). Se traduce a HTTP 404.
 * Cada microservicio crea una subclase propia (ej: ActividadNoEncontradaException)
 * y NO necesita configurar nada más: el manejador común la detecta por herencia.
 */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) { super(mensaje); }
}
