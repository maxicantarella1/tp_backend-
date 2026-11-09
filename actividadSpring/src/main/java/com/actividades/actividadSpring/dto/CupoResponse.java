package com.actividades.actividadSpring.dto;

import java.time.LocalDate;

/** Estado del cupo de una actividad en una fecha: máximo, ocupados y libres. */
public record CupoResponse(Long actividadId, LocalDate fecha, int cupoMaximo, int ocupados, int libres) { }
