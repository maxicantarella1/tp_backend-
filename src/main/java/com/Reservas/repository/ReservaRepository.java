package com.Reservas.repository;

import com.Reservas.model.EstadoReserva;
import com.Reservas.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Acceso a la tabla de reservas. Spring Data genera el CRUD y las consultas por nombre de método. */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** Reservas de un usuario (un turista solo puede ver las suyas). */
    List<Reserva> findByUsuario(String usuario);

    /** Reservas en ciertos estados cuya fecha cae en un rango (ambos extremos incluidos). */
    List<Reserva> findByEstadoInAndFechaBetween(Collection<EstadoReserva> estados, LocalDate desde, LocalDate hasta);
}
