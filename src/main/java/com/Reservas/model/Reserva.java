package com.Reservas.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Reserva de un usuario para una actividad en una fecha.
 * Guarda solo el id de la actividad (actividadId), no una relación JPA: la actividad vive en OTRO
 * microservicio y otra base de datos, entonces no hay JOIN ni clave foránea entre servicios.
 */
@Getter
@Setter
@Entity
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actividadId;   // referencia (por id) a la actividad del otro servicio
    private String usuario;
    private LocalDate fecha;
    private int cantidadPersonas;

    // Características de los participantes (se guardan para poder revalidar al reprogramar)
    private int edadMenor;
    private int adultos;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioTotal;

    /** STRING: guarda el nombre ("CONFIRMADA") y no el número; no se rompe si cambia el orden del enum. */
    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;

    /** Por qué está en este estado (ej: "Cancelada: probabilidad de lluvia 80%..."). Sirve para explicarle al usuario. */
    @Column(length = 500)
    private String motivo;

    /** Al cancelar: % del precio que se devuelve y el monto resultante. */
    private Integer porcentajeReintegro;
    @Column(precision = 12, scale = 2)
    private BigDecimal montoReintegro;
}
