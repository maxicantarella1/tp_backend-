package com.actividades.actividadSpring.exception;

import com.actividades.actividadSpring.model.Actividad;
import com.turismo.common.ConflictoNegocioException;

/** Se intentó reservar cupo en una actividad dada de baja -> conflicto de negocio (HTTP 409). */
public class ActividadInactivaException extends ConflictoNegocioException {
    public ActividadInactivaException(Long id) {
        super("Actividad dada de baja", "La actividad " + id + " está dada de baja y no admite reservas");
    }
}
