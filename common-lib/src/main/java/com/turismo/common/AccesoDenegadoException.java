package com.turismo.common;

/**
 * El usuario está autenticado pero NO puede hacer esta operación sobre este recurso
 * (ej: un turista intenta cancelar la reserva de otro). Se traduce a HTTP 403.
 * Es la excepción de las reglas de PROPIEDAD, que no se pueden expresar solo con roles.
 */
public class AccesoDenegadoException extends RuntimeException {
    /** Título corto del error: va en el campo "error" de la respuesta JSON (en vez del texto genérico del HTTP). */
    private final String titulo;

    /** Con título por defecto ("Acceso denegado"). */
    public AccesoDenegadoException(String mensaje) { this("Acceso denegado", mensaje); }

    /** Con un título propio de la regla (ej: "Fuera de temporada"). */
    public AccesoDenegadoException(String titulo, String mensaje) { super(mensaje); this.titulo = titulo; }

    public String getTitulo() { return titulo; }
}
