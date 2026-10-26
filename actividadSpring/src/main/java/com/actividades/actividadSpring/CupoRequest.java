package com.actividades.actividadSpring;

import jakarta.validation.constraints.Min;

/** Cuerpo del PATCH de cupo: { "cantidad": 3 }. @Min(1): no se puede pedir 0 ni negativos. */
public record CupoRequest(@Min(1) int cantidad) { }
