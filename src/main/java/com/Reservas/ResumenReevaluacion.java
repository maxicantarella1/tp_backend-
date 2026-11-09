package com.Reservas;

/** Resultado de reevaluar todas las reservas: cuántas se revisaron y cuántas cambiaron de estado. */
public record ResumenReevaluacion(int revisadas, int cambiadas, int errores) { }
