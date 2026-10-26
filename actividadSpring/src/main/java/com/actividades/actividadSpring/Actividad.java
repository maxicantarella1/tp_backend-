package com.actividades.actividadSpring;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Actividad turística. @Entity = se guarda como una tabla (JPA/Hibernate).
 * @Getter/@Setter de Lombok generan los métodos get/set al compilar.
 * Las anotaciones de validación (@NotBlank, @Min, @Positive) se aplican cuando el controller usa @Valid.
 */
@Getter
@Setter
@Entity
public class Actividad {

    /** Clave primaria autoincremental. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nombre;

    // Ubicación: se usa para pedirle a la API del clima el pronóstico DE ESE LUGAR.
    private double latitud;
    private double longitud;

    /** Lugares que quedan. Nunca puede ser negativo. */
    @Min(0)
    private int cupoDisponible;

    @Positive
    private double precio;

    // Límites climáticos: si el pronóstico los supera, la actividad no se puede realizar ese día.
    private int maxProbLluvia;       // % de probabilidad de lluvia
    private double maxVientoKmh;     // viento máximo tolerable
}
