package com.actividades.actividadSpring;

import com.turismo.common.RecursoNoEncontradoException;

/** El prestador pedido no existe -> HTTP 404 (lo traduce el manejador común). */
public class PrestadorNoEncontradoException extends RecursoNoEncontradoException {
    public PrestadorNoEncontradoException(Long id) { super("No existe el prestador " + id); }
}
