package com.Reservas;

import java.time.LocalTime;

/**
 * Pronóstico de UN día en UN lugar, ya "limpio" (lo que nos interesa de la respuesta de la API).
 * @param probLluvia  probabilidad máxima de lluvia del día (%)
 * @param vientoKmh   viento máximo a 10 m (km/h)
 * @param codigoClima código WMO del tiempo (95, 96 y 99 = tormenta eléctrica)
 * @param atardecer   hora local del atardecer (puede ser null si la API no lo informó)
 */
public record PronosticoDia(int probLluvia, double vientoKmh, int codigoClima, LocalTime atardecer) { }
