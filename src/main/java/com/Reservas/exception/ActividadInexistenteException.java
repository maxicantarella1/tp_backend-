package com.Reservas.exception;

import com.turismo.common.RecursoNoEncontradoException;

/** Actividades respondió 404: la actividad pedida no existe. Se traduce a HTTP 404. */
public class ActividadInexistenteException extends RecursoNoEncontradoException {
    public ActividadInexistenteException(Long id) {
        super("Actividad no encontrada", "No existe la actividad " + id);
    }
}
