package com.Reservas;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * DTO de ENTRADA: lo que el cliente manda al crear una reserva.
 * No expone la entidad ni deja elegir el estado (lo decide el sistema).
 */
public record ReservaRequest(
        @NotNull Long actividadId,
        @NotBlank String usuario,
        @NotNull @FutureOrPresent LocalDate fecha,   // no se reserva en el pasado
        @Min(1) int cantidadPersonas) { }
