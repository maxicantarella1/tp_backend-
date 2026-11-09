package com.actividades.actividadSpring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia la API externa de geocodificación.
 * La URL sale de application.properties (geocoding.url) para poder cambiarla sin tocar código.
 * Los TIMEOUTS evitan que una API externa lenta deje colgado a nuestro servicio.
 */
@Configuration
public class ClientesConfig {

    @Bean
    RestClient geocodingHttp(@Value("${geocoding.url}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);  // ms para conectar
        factory.setReadTimeout(3000);     // ms para recibir respuesta
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
}
