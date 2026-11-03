package com.actividades.actividadSpring.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Cuántos lugares están ocupados en una actividad EN UNA FECHA concreta.
 * Hay a lo sumo una fila por (actividad, fecha): lo garantiza la restricción única.
 * Lugares libres = actividad.cupoMaximo - ocupados.
 * Se crea la primera vez que alguien reserva ese día (no hace falta precargar el calendario).
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"actividadId", "fecha"}))
public class OcupacionDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actividadId;
    private LocalDate fecha;
    private int ocupados;
}
