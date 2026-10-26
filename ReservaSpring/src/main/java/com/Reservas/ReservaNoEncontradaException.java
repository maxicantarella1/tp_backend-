package com.Reservas;

import com.turismo.common.RecursoNoEncontradoException;

/** No existe la reserva buscada. Se traduce a HTTP 404. */
public class ReservaNoEncontradaException extends RecursoNoEncontradoException {
    public ReservaNoEncontradaException(Long id) {
        super("No existe la reserva " + id);
    }
}
