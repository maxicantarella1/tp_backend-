package com.Reservas.exception;

import com.turismo.common.ConflictoNegocioException;

/**
 * La actividad no se puede reservar/realizar por una regla de negocio (fuera de temporada, edad,
 * feriado, clima desfavorable...). El mensaje explica el motivo. Se traduce a HTTP 409.
 */
public class ActividadNoRealizableException extends ConflictoNegocioException {
    /** titulo = nombre corto de la regla (ej: "Fuera de temporada"); motivo = explicación con los datos. */
    public ActividadNoRealizableException(String titulo, String motivo) { super(titulo, motivo); }
    public ActividadNoRealizableException(String motivo) { super("Actividad no realizable", motivo); }
}
