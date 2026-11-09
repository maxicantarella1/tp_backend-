package com.actividades.actividadSpring;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface OcupacionDiaRepository extends JpaRepository<OcupacionDia, Long> {

    /** Lectura simple (para consultar disponibilidad). */
    Optional<OcupacionDia> findByActividadIdAndFecha(Long actividadId, LocalDate fecha);

    /**
     * Lectura con BLOQUEO de escritura (SELECT ... FOR UPDATE).
     * Evita que dos reservas simultáneas lean "quedan 2 lugares" y ambas se los lleven (sobreventa):
     * la segunda espera a que termine la transacción de la primera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OcupacionDia o where o.actividadId = :actividadId and o.fecha = :fecha")
    Optional<OcupacionDia> buscarBloqueando(@Param("actividadId") Long actividadId,
                                            @Param("fecha") LocalDate fecha);
}
