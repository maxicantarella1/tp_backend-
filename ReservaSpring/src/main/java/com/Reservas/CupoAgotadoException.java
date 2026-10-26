package com.Reservas;

import com.turismo.common.ConflictoNegocioException;

/** Actividades respondió 409: no alcanza el cupo pedido. Se traduce a HTTP 409. */
public class CupoAgotadoException extends ConflictoNegocioException {
    public CupoAgotadoException(Long id) {
        super("No hay cupo suficiente en la actividad " + id);
    }
}
