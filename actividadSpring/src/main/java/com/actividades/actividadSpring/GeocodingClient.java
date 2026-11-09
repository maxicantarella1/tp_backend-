package com.actividades.actividadSpring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.turismo.common.ServicioNoDisponibleException;
import com.turismo.common.SolicitudInvalidaException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Integración con la API pública de GEOCODIFICACIÓN de Open-Meteo (sin clave):
 *   GET https://geocoding-api.open-meteo.com/v1/search?name=Villa General Belgrano&count=1&countryCode=AR
 *
 * POR QUÉ SE NECESITA: el pronóstico del clima se pide por coordenadas, pero el prestador carga
 * actividades por nombre de lugar. Resolverlo con una tabla propia sería mantener a mano miles de
 * localidades. Se usa solo al CREAR la actividad (no en cada reserva).
 *
 * SI FALLA: localidad inexistente -> 400 (error del dato). API caída -> 503, y el prestador
 * puede cargar latitud/longitud a mano y evitar la dependencia.
 */
@Component
public class GeocodingClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(double latitude, double longitude, String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(List<Resultado> results) { }

    private final RestClient http;

    public GeocodingClient(@Qualifier("geocodingHttp") RestClient http) {
        this.http = http;
    }

    public Coordenadas buscar(String localidad) {
        Respuesta r;
        try {
            r = http.get()
                    .uri(b -> b.path("/v1/search")
                            .queryParam("name", "{nombre}")
                            .queryParam("count", 1)
                            .queryParam("language", "es")
                            .queryParam("countryCode", "AR")
                            .build(localidad))
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientException e) {
            // sin conexión, timeout, 5xx o respuesta ilegible: no podemos resolver la ubicación ahora
            throw new ServicioNoDisponibleException("geocoding (open-meteo)");
        }
        if (r == null || r.results() == null || r.results().isEmpty()) {
            throw new SolicitudInvalidaException("No se encontró la localidad '" + localidad
                    + "'. Revisá el nombre o informá latitud y longitud.");
        }
        Resultado primero = r.results().get(0);
        return new Coordenadas(primero.latitude(), primero.longitude());
    }
}
