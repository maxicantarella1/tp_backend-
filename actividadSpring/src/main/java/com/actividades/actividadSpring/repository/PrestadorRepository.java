package com.actividades.actividadSpring.repository;

import com.actividades.actividadSpring.model.Prestador;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla de prestadores (CRUD generado por Spring Data). */
public interface PrestadorRepository extends JpaRepository<Prestador, Long> { }
