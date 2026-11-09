package com.actividades.actividadSpring;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Actividad turística. Guarda TODAS las condiciones que determinan si puede realizarse:
 * ubicación, capacidad, horario, época del año, participantes, feriados y límites ambientales.
 * La decisión final ("se puede reservar / se puede realizar") la toma el servicio de Reservas
 * comparando estas condiciones con la fecha pedida y los datos reales de las APIs externas.
 *
 * @Entity = tabla JPA. Las anotaciones de validación se aplican con @Valid en el controller.
 */
@Getter
@Setter
@Entity
public class Actividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nombre;

    /** A qué prestador pertenece (por id). Se valida que exista al crear la actividad. */
    @NotNull
    private Long prestadorId;

    // ---------------- Ubicación ----------------
    // Se informan latitud+longitud O una localidad (ej: "Villa General Belgrano").
    // Si solo viene la localidad, el servicio consulta la API de geocodificación y completa las coordenadas.
    private String localidad;
    @DecimalMin("-90") @DecimalMax("90")
    private Double latitud;
    @DecimalMin("-180") @DecimalMax("180")
    private Double longitud;

    // ---------------- Capacidad ----------------
    /** Lugares disponibles POR DÍA (por salida). El cupo se controla por fecha (ver OcupacionDia). */
    @Min(1)
    private int cupoMaximo;

    /**
     * Cupo mínimo para que la salida se realice (0 = sin mínimo). Si 24 h antes no se alcanzó,
     * la salida no se hace y el servicio de Reservas cancela las reservas de ese día con reintegro total.
     */
    @Min(0)
    private int cupoMinimo;

    @Positive
    private double precio;

    // ---------------- Horario ----------------
    /** Hora de inicio de la actividad (formato "09:00"). */
    @NotNull
    private LocalTime horaInicio;

    /** Duración en horas. Con horaInicio permite saber si termina antes del atardecer. */
    @Min(1) @Max(24)
    private int duracionHoras;

    // ---------------- Participantes ----------------
    /** Edad mínima exigida (0 = sin restricción). Se compara contra el participante más joven. */
    @Min(0)
    private int edadMinima;

    // ---------------- Época del año (meses 1 a 12) ----------------
    // Si desde > hasta la temporada cruza fin de año. Ej: 11 a 3 = noviembre a marzo.
    @Min(1) @Max(12)
    private int temporadaDesdeMes;
    @Min(1) @Max(12)
    private int temporadaHastaMes;

    // ---------------- Feriados ----------------
    /** Si es false, la actividad NO opera los feriados nacionales. */
    private boolean operaFeriados = true;

    /** Recargo de precio (0..100 %) cuando la fecha es feriado y la actividad sí opera. */
    @Min(0) @Max(100)
    private int recargoFeriadoPct = 0;

    // ---------------- Condiciones externas ----------------
    /** true = depende del clima. false = actividad bajo techo: no se evalúa clima ni aire. */
    private boolean aireLibre = true;

    @Min(0) @Max(100)
    private int maxProbLluvia;       // % de probabilidad de lluvia tolerado
    @PositiveOrZero
    private double maxVientoKmh;     // viento máximo tolerado
    /** Índice de calidad del aire máximo tolerado (US AQI: 0-50 bueno, 101-150 dañino p/ sensibles). */
    @Min(0)
    private int maxAqi = 150;

    /**
     * Baja lógica: en lugar de borrar la fila se marca inactiva, así las reservas existentes
     * que la referencian no quedan huérfanas. Una actividad inactiva no admite nuevas reservas.
     */
    private boolean activa = true;
}
