package com.Reservas.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Cuerpo de la reprogramación: { "fecha": "2026-11-20" }. */
public record ReprogramarRequest(@NotNull @FutureOrPresent LocalDate fecha) { }
