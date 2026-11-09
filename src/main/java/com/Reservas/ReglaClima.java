package com.Reservas;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decide si las CONDICIONES EXTERNAS permiten realizar la actividad ese día, comparando los límites de
 * la actividad con el pronóstico y la calidad del aire. Clase de funciones puras (fácil de testear).
 *
 *  FAVORABLE    : hay pronóstico y nada supera los límites.
 *  DESFAVORABLE : algo supera un límite (el motivo queda en el detalle).
 *  SIN_DATOS    : no hay pronóstico (fecha lejana o API caída): no se puede afirmar nada -> reserva PENDIENTE.
 */
public final class ReglaClima {

    private ReglaClima() { }

    public enum Veredicto { FAVORABLE, DESFAVORABLE, SIN_DATOS }

    public record Resultado(Veredicto veredicto, String detalle) { }

    /** Códigos WMO 95, 96 y 99 = tormenta eléctrica. */
    static final int CODIGO_TORMENTA = 95;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public static Resultado evaluar(ActividadDto a, LocalDate fecha,
                                    Optional<PronosticoDia> pronostico, Optional<CalidadAire> aire) {
        // Actividad bajo techo: el clima no la condiciona
        if (!a.aireLibre()) {
            return new Resultado(Veredicto.FAVORABLE, "Actividad bajo techo: no depende del clima");
        }
        List<String> problemas = new ArrayList<>();

        pronostico.ifPresent(p -> {
            if (p.probLluvia() > a.maxProbLluvia()) {
                problemas.add("probabilidad de lluvia " + p.probLluvia() + "% (máximo tolerado " + a.maxProbLluvia() + "%)");
            }
            if (p.vientoKmh() > a.maxVientoKmh()) {
                problemas.add("viento de " + Math.round(p.vientoKmh()) + " km/h (máximo tolerado " + Math.round(a.maxVientoKmh()) + " km/h)");
            }
            if (p.codigoClima() >= CODIGO_TORMENTA) {
                problemas.add("tormenta eléctrica prevista");
            }
            // Debe terminar antes del atardecer
            if (p.atardecer() != null && a.horaInicio() != null) {
                LocalDateTime fin = fecha.atTime(a.horaInicio()).plusHours(a.duracionHoras());
                LocalDateTime sunset = fecha.atTime(p.atardecer());
                if (fin.isAfter(sunset)) {
                    problemas.add("terminaría a las " + fin.format(HORA) + ", después del atardecer (" + p.atardecer().format(HORA) + ")");
                }
            }
        });

        // El aire se evalúa aparte: si hay dato y supera el límite, alcanza para declararla inviable
        aire.ifPresent(c -> {
            if (a.maxAqi() > 0 && c.aqiMaximo() > a.maxAqi()) {
                problemas.add("calidad del aire AQI " + c.aqiMaximo() + " (máximo tolerado " + a.maxAqi() + ")");
            }
        });

        if (!problemas.isEmpty()) {
            return new Resultado(Veredicto.DESFAVORABLE, String.join("; ", problemas));
        }
        if (pronostico.isEmpty()) {
            return new Resultado(Veredicto.SIN_DATOS, "Sin pronóstico confiable para esa fecha");
        }
        return new Resultado(Veredicto.FAVORABLE, "Pronóstico favorable");
    }
}
