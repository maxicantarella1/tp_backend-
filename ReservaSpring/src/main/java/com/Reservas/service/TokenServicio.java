package com.Reservas.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.turismo.common.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;

/**
 * Obtiene de Keycloak el token de la CUENTA DE SERVICIO de Reservas (flujo OAuth2 "client credentials").
 *
 * POR QUÉ HACE FALTA: Reservas llama a Actividades para descontar/devolver cupo, y esa operación exige el
 * rol "servicio". Además la reevaluación programada corre SIN usuario (no hay token de turista que reenviar).
 * Entonces Reservas se identifica a sí mismo ante Keycloak con su client_id + client_secret y recibe un JWT
 * con el rol "servicio". Ese JWT se adjunta a cada llamada hacia Actividades.
 *
 * El token se guarda en memoria y solo se renueva cuando está por vencer (no se pide uno por cada llamada).
 * En producción el secreto NO va en el código: se inyecta por variable de entorno (KEYCLOAK_CLIENT_SECRET).
 */
@Component
public class TokenServicio {

    private static final Logger log = LoggerFactory.getLogger(TokenServicio.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(@JsonProperty("access_token") String accessToken,
                     @JsonProperty("expires_in") long expiraEnSegundos) { }

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final Clock clock;

    private String token;
    private Instant expira = Instant.EPOCH;

    public TokenServicio(@Value("${keycloak.token-url}") String tokenUrl,
                         @Value("${keycloak.client-id}") String clientId,
                         @Value("${keycloak.client-secret}") String clientSecret,
                         Clock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.http = RestClient.builder().requestFactory(factory).build();
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    /** Devuelve un token vigente (renueva si falta menos de 30 s para que venza). synchronized: un solo hilo renueva. */
    public synchronized String token() {
        if (token == null || Instant.now(clock).isAfter(expira.minusSeconds(30))) {
            renovar();
        }
        return token;
    }

    private void renovar() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            Respuesta r = http.post().uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Respuesta.class);
            if (r == null || r.accessToken() == null) {
                throw new ServicioNoDisponibleException("keycloak (respuesta sin token)");
            }
            token = r.accessToken();
            expira = Instant.now(clock).plusSeconds(r.expiraEnSegundos());
        } catch (RestClientException e) {
            log.error("No se pudo obtener el token de servicio de Keycloak: {}", e.toString());
            throw new ServicioNoDisponibleException("keycloak");
        }
    }
}
