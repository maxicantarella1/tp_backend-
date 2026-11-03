package com.Reservas.client;

import com.Reservas.dto.PronosticoDia;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Integración con la API pública de PRONÓSTICO de Open-Meteo (sin clave):
 *   GET https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..
 *       &daily=precipitation_probability_max,wind_speed_10m_max,weather_code,sunset
 *       &timezone=auto&start_date=2026-11-10&end_date=2026-11-10
 *
 * POR QUÉ SE NECESITA: decide si la actividad se puede REALIZAR ese día (lluvia, viento, tormenta,
 * atardecer). Es información que cambia día a día y no se puede guardar como configuración.
 *
 * SI FALLA O NO HAY DATO: devuelve Optional.empty() (nunca lanza). La reserva queda PENDIENTE en vez
 * de rechazarse: la API del clima caída no debe impedir vender, y la reevaluación posterior resuelve.
 * HORIZONTE: la API pronostica hasta ~16 días; más allá ni siquiera se consulta.
 */
@Component
public class ClimaClient {

    private static final Logger log = LoggerFactory.getLogger(ClimaClient.class);
    /** Hoy + 15 días = 16 días de pronóstico. */
    public static final int HORIZONTE_DIAS = 15;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Diario(List<String> time,
                  List<Double> precipitation_probability_max,
                  List<Double> wind_speed_10m_max,
                  List<Integer> weather_code,
                  List<String> sunset) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(Diario daily) { }

    private final RestClient http;
    private final Clock clock;

    public ClimaClient(@Qualifier("climaHttp") RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    public Optional<PronosticoDia> pronostico(double latitud, double longitud, LocalDate fecha) {
        LocalDate hoy = LocalDate.now(clock);
        if (fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(HORIZONTE_DIAS))) {
            return Optional.empty();   // fuera del horizonte: no hay pronóstico confiable
        }
        try {
            Respuesta r = http.get()
                    .uri(b -> b.path("/v1/forecast")
                            .queryParam("latitude", "{lat}")
                            .queryParam("longitude", "{lon}")
                            .queryParam("daily", "precipitation_probability_max,wind_speed_10m_max,weather_code,sunset")
                            .queryParam("timezone", "auto")
                            .queryParam("start_date", "{dia}")
                            .queryParam("end_date", "{dia}")
                            .build(Map.of("lat", latitud, "lon", longitud, "dia", fecha.toString())))
                    .retrieve()
                    .body(Respuesta.class);
            return interpretar(r);
        } catch (RuntimeException e) {
            // timeout, sin conexión, 4xx/5xx o JSON inesperado: degradamos, no rompemos la reserva
            log.warn("API de clima no disponible o con respuesta inesperada ({}): {}", fecha, e.toString());
            return Optional.empty();
        }
    }

    /** Extrae el primer (y único) día de la respuesta validando que los datos existan. */
    private Optional<PronosticoDia> interpretar(Respuesta r) {
        if (r == null || r.daily() == null) return Optional.empty();
        Diario d = r.daily();
        Double lluvia = primero(d.precipitation_probability_max());
        Double viento = primero(d.wind_speed_10m_max());
        if (lluvia == null || viento == null) return Optional.empty();   // sin los datos clave no hay veredicto
        Integer codigo = primero(d.weather_code());
        String sunset = primero(d.sunset());
        return Optional.of(new PronosticoDia(
                (int) Math.round(lluvia), viento,
                codigo == null ? 0 : codigo,
                sunset == null ? null : LocalDateTime.parse(sunset).toLocalTime()));
    }

    private static <T> T primero(List<T> lista) {
        return (lista == null || lista.isEmpty()) ? null : lista.get(0);
    }
}
