package com.actividades.actividadSpring.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Cuerpo de los PATCH de cupo: { "fecha": "2026-11-10", "cantidad": 3 }. */
public record CupoRequest(@NotNull LocalDate fecha, @Min(1) int cantidad) { }
