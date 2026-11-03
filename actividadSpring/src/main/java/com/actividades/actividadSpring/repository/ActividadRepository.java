package com.actividades.actividadSpring.repository;

import com.actividades.actividadSpring.model.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla de actividades. Al extender JpaRepository, Spring Data genera SOLO la
 * implementación de save, findById, findAll, deleteById... No hay que escribir SQL.
 */
public interface ActividadRepository extends JpaRepository<Actividad, Long> { }
