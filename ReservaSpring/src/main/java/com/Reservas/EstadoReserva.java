package com.Reservas;

/**
 * Ciclo de vida de una reserva.
 * PENDIENTE  : cupo descontado pero sin pronóstico confiable (fecha lejana o API de clima caída).
 * CONFIRMADA : pronóstico favorable.
 * CANCELADA  : por el usuario o porque el clima la volvió inviable.
 * (Hoy solo se usa CONFIRMADA; PENDIENTE y CANCELADA se implementan en los próximos pasos.)
 */
public enum EstadoReserva { PENDIENTE, CONFIRMADA, CANCELADA }
