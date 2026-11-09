package com.Reservas;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Política de REINTEGRO cuando el USUARIO cancela, según la anticipación:
 *   48 h o más antes -> 100%   ·   entre 24 y 48 h -> 50%   ·   menos de 24 h -> 0%
 * Cuando cancela el SISTEMA (clima, feriado, cupo mínimo, baja de la actividad) el reintegro es siempre 100%:
 * el usuario no tiene la culpa. Ese caso no pasa por acá.
 */
public final class PoliticaCancelacion {

    private PoliticaCancelacion() { }

    public static int porcentajeReintegroUsuario(LocalDate fechaActividad, LocalDateTime ahora) {
        long horas = Duration.between(ahora, fechaActividad.atStartOfDay()).toHours();
        if (horas >= 48) return 100;
        if (horas >= 24) return 50;
        return 0;
    }
}
