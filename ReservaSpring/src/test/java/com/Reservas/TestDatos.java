package com.Reservas;

import com.Reservas.dto.ActividadDto;
import java.time.LocalTime;

/** Datos de prueba reutilizables: una actividad "base" válida (trekking, temporada oct-mar, cupo 10, 12+ años...). */
final class TestDatos {

    private TestDatos() { }

    /** Orden: id, nombre, activa, lat, lon, precio, cupoMax, cupoMin, horaInicio, duración, edadMin, desde, hasta,
     *  operaFeriados, recargo%, aireLibre, maxLluvia, maxViento, maxAqi */
    static ActividadDto base() {
        return new ActividadDto(1L, "Trekking", true, -31.4, -64.2, 1000.0, 10, 0,
                LocalTime.of(9, 0), 6, 12, 10, 3, true, 20, true, 30, 40.0, 150);
    }

    /** Variante con los datos que más cambian en los tests. */
    static ActividadDto base(boolean activa, int desde, int hasta, int edadMin, int cupoMax, boolean aireLibre) {
        return new ActividadDto(1L, "Trekking", activa, -31.4, -64.2, 1000.0, cupoMax, 0,
                LocalTime.of(9, 0), 6, edadMin, desde, hasta, true, 20, aireLibre, 30, 40.0, 150);
    }
}
