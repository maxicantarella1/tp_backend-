package com.Reservas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Integración con la API pública de CALIDAD DEL AIRE de Open-Meteo (sin clave):
 *   GET https://air-quality-api.open-meteo.com/v1/air-quality?latitude=..&longitude=..
 *       &hourly=us_aqi&timezone=auto&start_date=..&end_date=..
 *
 * POR QUÉ SE NECESITA: en las sierras de Córdoba el humo de incendios puede volver peligrosa una
 * actividad física al aire libre aunque no llueva ni haya viento. Es un dato externo que cambia por hora.
 *
 * Es una condición ADICIONAL: si no hay dato (fecha lejana o API caída) simplemente no se aplica y NO
 * fuerza PENDIENTE (a diferencia del clima), porque el pronóstico de aire solo existe para pocos días.
 */
@Component
public class AireClient {

    private static final Logger log = LoggerFactory.getLogger(AireClient.class);
    /** La API ofrece pocos días de pronóstico de aire: hoy + 4. */
    static final int HORIZONTE_DIAS = 4;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PorHora(List<Double> us_aqi) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(PorHora hourly) { }

    private final RestClient http;
    private final Clock clock;

    public AireClient(@Qualifier("aireHttp") RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    public Optional<CalidadAire> calidad(double latitud, double longitud, LocalDate fecha) {
        LocalDate hoy = LocalDate.now(clock);
        if (fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(HORIZONTE_DIAS))) {
            return Optional.empty();
        }
        try {
            Respuesta r = http.get()
                    .uri(b -> b.path("/v1/air-quality")
                            .queryParam("latitude", "{lat}")
                            .queryParam("longitude", "{lon}")
                            .queryParam("hourly", "us_aqi")
                            .queryParam("timezone", "auto")
                            .queryParam("start_date", "{dia}")
                            .queryParam("end_date", "{dia}")
                            .build(Map.of("lat", latitud, "lon", longitud, "dia", fecha.toString())))
                    .retrieve()
                    .body(Respuesta.class);
            if (r == null || r.hourly() == null || r.hourly().us_aqi() == null) return Optional.empty();
            // La API devuelve 24 valores (uno por hora): nos importa el PEOR del día
            return r.hourly().us_aqi().stream().filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue).max().stream()
                    .mapToObj(max -> new CalidadAire((int) Math.round(max)))
                    .findFirst();
        } catch (RuntimeException e) {
            log.warn("API de calidad del aire no disponible ({}): {}", fecha, e.toString());
            return Optional.empty();
        }
    }
}
