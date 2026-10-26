package com.Reservas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo que Reservas "sabe" de una actividad: solo los campos que necesita.
 * NO es la entidad de Actividades: cada servicio tiene su propio modelo (así no se acoplan).
 * ignoreUnknown=true: si Actividades agrega campos nuevos a su JSON, Reservas no se rompe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActividadDto(Long id, String nombre, int cupoDisponible,
                           double latitud, double longitud,
                           int maxProbLluvia, double maxVientoKmh) { }
