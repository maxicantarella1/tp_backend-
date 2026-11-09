package com.turismo.common;

/**
 * Base para "la petición es válida pero rompe una regla de negocio"
 * (sin cupo, clima desfavorable, reserva ya cancelada...). Se traduce a HTTP 409 (Conflict).
 * Los servicios crean subclases (CupoAgotadoException, CupoInsuficienteException).
 */
public class ConflictoNegocioException extends RuntimeException {
    /** Título corto del error: va en el campo "error" de la respuesta JSON (en vez del texto genérico del HTTP). */
    private final String titulo;

    /** Con título por defecto ("Regla de negocio incumplida"). */
    public ConflictoNegocioException(String mensaje) { this("Regla de negocio incumplida", mensaje); }

    /** Con un título propio de la regla (ej: "Fuera de temporada"). */
    public ConflictoNegocioException(String titulo, String mensaje) { super(mensaje); this.titulo = titulo; }

    public String getTitulo() { return titulo; }
}
