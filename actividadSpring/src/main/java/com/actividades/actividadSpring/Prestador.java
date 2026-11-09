package com.actividades.actividadSpring;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Prestador turístico: la empresa u operador que ofrece actividades (ej: "Sierras Aventura").
 * El dominio dice que la plataforma administra actividades "de diferentes prestadores".
 */
@Getter
@Setter
@Entity
public class Prestador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nombre;

    /** Dato de contacto libre (mail o teléfono). Opcional. */
    private String contacto;

    /**
     * Usuario de Keycloak que ES DUEÑO de este prestador (su "preferred_username").
     * Keycloak guarda QUIÉN es el usuario y su ROL; la relación de negocio "este usuario administra este
     * prestador" vive acá, en la base del dominio. Se usa para la regla: un prestador solo gestiona lo suyo.
     */
    private String usuario;
}
