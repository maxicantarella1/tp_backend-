package com.Reservas.model;

/**
 * Ciclo de vida de una reserva.
 * PENDIENTE  : cupo reservado pero falta información externa para confirmar (pronóstico lejano/API caída,
 *              feriados sin consultar). Se revisa de nuevo con la reevaluación.
 * CONFIRMADA : todos los datos disponibles y favorables.
 * CANCELADA  : por el usuario o por el sistema (clima, feriado, cupo mínimo, baja de la actividad).
 */
public enum EstadoReserva { PENDIENTE, CONFIRMADA, CANCELADA }
