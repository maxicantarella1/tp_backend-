package com.Reservas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configura el cliente HTTP con el que Reservas le habla a Actividades.
 * La URL base sale de application.properties (actividades.url), así en Docker solo se cambia
 * esa propiedad por http://actividades:8081 sin tocar código.
 */
@Configuration
public class ClientesConfig {

    @Bean
    RestClient actividadesClient(@Value("${actividades.url}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // TIMEOUTS: si Actividades no responde, no nos quedamos colgados; fallamos rápido (503).
        factory.setConnectTimeout(2000);  // ms para conectar
        factory.setReadTimeout(3000);     // ms para recibir la respuesta
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }
}
