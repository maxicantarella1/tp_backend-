package com.Reservas;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Reserva de un usuario para una actividad en una fecha.
 * Guarda solo el id de la actividad (actividadId), no una relación JPA: la actividad vive en OTRO
 * microservicio y otra base de datos, entonces no se puede hacer un JOIN ni una clave foránea.
 * CAMBIO: se quitó @Data (genera equals/hashCode, desaconsejado en entidades JPA); quedan @Getter/@Setter.
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

    /** STRING: guarda el nombre ("CONFIRMADA") y no el número; no se rompe si cambia el orden del enum. */
    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;
}
