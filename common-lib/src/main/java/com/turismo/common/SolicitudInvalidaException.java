package com.turismo.common;

/**
 * La petición llegó bien formada pero sus datos no tienen sentido para el negocio
 * (ej: falta la ubicación, la localidad no existe). Se traduce a HTTP 400.
 * Diferencia con ConflictoNegocioException (409): acá el error es del DATO enviado,
 * no de un estado del sistema (cupo, clima...).
 */
public class SolicitudInvalidaException extends RuntimeException {
    /** Título corto del error: va en el campo "error" de la respuesta JSON (en vez del texto genérico del HTTP). */
    private final String titulo;

    /** Con título por defecto ("Solicitud inválida"). */
    public SolicitudInvalidaException(String mensaje) { this("Solicitud inválida", mensaje); }

    /** Con un título propio de la regla (ej: "Fuera de temporada"). */
    public SolicitudInvalidaException(String titulo, String mensaje) { super(mensaje); this.titulo = titulo; }

    public String getTitulo() { return titulo; }
}
