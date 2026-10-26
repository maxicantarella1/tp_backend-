package com.Reservas;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla de reservas (CRUD generado por Spring Data JPA). */
public interface ReservaRepository extends JpaRepository<Reserva, Long> { }
