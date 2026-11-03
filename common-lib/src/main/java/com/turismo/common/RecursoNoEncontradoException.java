package com.turismo.common;

/**
 * Base para "no existe lo que pidieron" (actividad, reserva...). Se traduce a HTTP 404.
 * Cada microservicio crea una subclase propia (ej: ActividadNoEncontradaException)
 * y NO necesita configurar nada más: el manejador común la detecta por herencia.
 */
public class RecursoNoEncontradoException extends RuntimeException {
    /** Título corto del error: va en el campo "error" de la respuesta JSON (en vez del texto genérico del HTTP). */
    private final String titulo;

    /** Con título por defecto ("Recurso no encontrado"). */
    public RecursoNoEncontradoException(String mensaje) { this("Recurso no encontrado", mensaje); }

    /** Con un título propio de la regla (ej: "Fuera de temporada"). */
    public RecursoNoEncontradoException(String titulo, String mensaje) { super(mensaje); this.titulo = titulo; }

    public String getTitulo() { return titulo; }
}
