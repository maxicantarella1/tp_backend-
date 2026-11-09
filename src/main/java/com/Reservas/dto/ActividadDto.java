package com.Reservas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalTime;

/**
 * Lo que Reservas "sabe" de una actividad: SOLO los campos que necesita para decidir.
 * NO es la entidad de Actividades: cada servicio tiene su propio modelo (así no se acoplan).
 * ignoreUnknown=true: si Actividades agrega campos nuevos a su JSON, Reservas no se rompe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActividadDto(
        Long id, String nombre, boolean activa,
        Double latitud, Double longitud,
        double precio, int cupoMaximo, int cupoMinimo,
        LocalTime horaInicio, int duracionHoras,
        int edadMinima, int temporadaDesdeMes, int temporadaHastaMes,
        boolean operaFeriados, int recargoFeriadoPct,
        boolean aireLibre, int maxProbLluvia, double maxVientoKmh, int maxAqi) { }
