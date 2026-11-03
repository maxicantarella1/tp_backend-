package com.actividades.actividadSpring.exception;

import com.actividades.actividadSpring.model.Actividad;
import com.turismo.common.RecursoNoEncontradoException;

/** La actividad pedida no existe. Hereda de la clase común -> HTTP 404 automático. */
public class ActividadNoEncontradaException extends RecursoNoEncontradoException {
    public ActividadNoEncontradaException(Long id) { super("Actividad no encontrada", "No existe la actividad " + id); }
}
