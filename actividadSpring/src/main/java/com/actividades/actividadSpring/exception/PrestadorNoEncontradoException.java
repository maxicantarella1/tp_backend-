package com.actividades.actividadSpring.exception;

import com.actividades.actividadSpring.model.Prestador;
import com.turismo.common.RecursoNoEncontradoException;

/** El prestador pedido no existe -> HTTP 404 (lo traduce el manejador común). */
public class PrestadorNoEncontradoException extends RecursoNoEncontradoException {
    public PrestadorNoEncontradoException(Long id) { super("Prestador no encontrado", "No existe el prestador " + id); }
}
