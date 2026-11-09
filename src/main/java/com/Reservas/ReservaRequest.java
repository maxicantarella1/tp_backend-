package com.Reservas;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * DTO de ENTRADA: lo que el cliente manda al crear una reserva.
 * No expone la entidad ni deja elegir estado/precio (los decide el sistema).
 *
 * "usuario" es OPCIONAL y casi siempre se ignora: el dueño de la reserva es el usuario del TOKEN (no se
 * puede reservar "a nombre de otro" falsificando este campo). Solo un admin puede indicar otro usuario.
 */
public record ReservaRequest(
        @NotNull Long actividadId,
        String usuario,
        @NotNull @FutureOrPresent LocalDate fecha,   // no se reserva en el pasado
        @Min(1) int cantidadPersonas,
        @Min(0) int edadMenor,                       // edad del participante MÁS JOVEN del grupo
        @Min(0) int adultos) { }                     // cuántos del grupo son adultos
