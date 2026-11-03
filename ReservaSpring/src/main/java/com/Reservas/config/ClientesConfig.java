package com.Reservas.config;

import com.Reservas.service.TokenServicio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * Clientes HTTP de este servicio: uno hacia Actividades (otro microservicio) y tres hacia APIs
 * públicas externas. Las URLs salen de application.properties, así en Docker/tests se cambian sin
 * tocar código. TODOS tienen timeouts: si el otro lado no responde, fallamos rápido en vez de colgarnos.
 * Hay varios beans del mismo tipo (RestClient); cada clase elige el suyo con @Qualifier("nombre").
 */
@Configuration
public class ClientesConfig {

    /**
     * Cliente hacia Actividades: a cada petición se le agrega "Authorization: Bearer <token de servicio>".
     * El interceptor se ejecuta justo antes de enviar; así ReservaService no se ocupa de la seguridad.
     * Usa JdkClientHttpRequestFactory (HttpClient de Java 17) porque soporta el método HTTP PATCH.
     */
    @Bean
    RestClient actividadesClient(@Value("${actividades.url}") String url, TokenServicio tokens) {
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(crearFactoryConTimeout())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokens.token());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    RestClient climaHttp(@Value("${clima.url}") String url) { return cliente(url); }

    @Bean
    RestClient aireHttp(@Value("${aire.url}") String url) { return cliente(url); }

    @Bean
    RestClient feriadosHttp(@Value("${feriados.url}") String url) { return cliente(url); }

    /** Reloj inyectable: en los tests se reemplaza por uno fijo para probar fechas sin depender de "hoy". */
    @Bean
    Clock reloj() { return Clock.systemDefaultZone(); }

    private RestClient cliente(String url) {
        return RestClient.builder().baseUrl(url).requestFactory(crearFactoryConTimeout()).build();
    }

    private JdkClientHttpRequestFactory crearFactoryConTimeout() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2000)) // ms para conectar
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(3000)); // ms para recibir la respuesta
        return factory;
    }
}
