package com.actividades.actividadSpring;

import com.turismo.common.RecursoNoEncontradoException;

/** La actividad pedida no existe. Hereda de la clase común -> HTTP 404 automático. */
public class ActividadNoEncontradaException extends RecursoNoEncontradoException {
    public ActividadNoEncontradaException(Long id) { super("No existe la actividad " + id); }
}
