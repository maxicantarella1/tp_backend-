package com.actividades.actividadSpring;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

/**
 * Capa de NEGOCIO de actividades: acá viven las reglas del cupo.
 * El controller recibe HTTP, este service decide, el repository habla con la base.
 * @Transactional: todo el método corre en una transacción; si lanza excepción se hace rollback.
 */
@Service
public class ActividadService {

    private final ActividadRepository repo;

    /** Inyección por constructor (deja el campo final y facilita los tests). */
    public ActividadService(ActividadRepository repo) {
        this.repo = repo;
    }

    /**
     * Descuenta cupo. REGLA: no se puede descontar más de lo disponible.
     * Esta regla vive ACÁ (no en Reservas) porque Actividades es el dueño del cupo.
     * Si no alcanza -> CupoInsuficienteException (409).
     */
    @Transactional
    public Actividad descontarCupo(Long id, int cantidad) {
        Actividad a = repo.findById(id)
                .orElseThrow(() -> new ActividadNoEncontradaException(id));

        if (a.getCupoDisponible() < cantidad) {
            throw new CupoInsuficienteException(id, cantidad, a.getCupoDisponible());
        }
        a.setCupoDisponible(a.getCupoDisponible() - cantidad);
        return a; // al terminar la transacción, JPA guarda el cambio solo (dirty checking)
    }
}
