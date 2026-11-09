package com.Reservas;

import com.turismo.common.ConflictoNegocioException;

/**
 * La actividad no se puede reservar/realizar por una regla de negocio (fuera de temporada, edad,
 * feriado, clima desfavorable...). El mensaje explica el motivo. Se traduce a HTTP 409.
 */
public class ActividadNoRealizableException extends ConflictoNegocioException {
    public ActividadNoRealizableException(String motivo) { super(motivo); }
}
