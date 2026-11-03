package com.Reservas.dto;

/** Peor índice de calidad del aire (US AQI) previsto para el día. 0-50 bueno · 101-150 dañino p/ sensibles · 151+ dañino. */
public record CalidadAire(int aqiMaximo) { }
