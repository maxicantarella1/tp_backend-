package com.actividades.actividadSpring.exception;

import com.actividades.actividadSpring.model.Actividad;
import com.turismo.common.ConflictoNegocioException;

/** No alcanza el cupo pedido. Es un conflicto de negocio -> el manejador común responde HTTP 409. */
public class CupoInsuficienteException extends ConflictoNegocioException {
    public CupoInsuficienteException(Long id, int pedido, int disponible) {
        super("Cupo insuficiente", "Actividad " + id + ": se piden " + pedido + " lugares y hay " + disponible);
    }
}
