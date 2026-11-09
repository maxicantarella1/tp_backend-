package com.gateway.config;

import com.turismo.common.ErrorResponse;
import com.turismo.common.Seguridad;
import com.turismo.common.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.ConnectException;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * Configuración de ENRUTAMIENTO (Routing) del API Gateway.
 *
 * Define hacia qué microservicio funcional interno se encamina cada ruta pública:
 *  - /actividades/** y /prestadores/**  -->  actividades-service (puerto 8081)
 *  - /reservas/**                       -->  reservas-service    (puerto 8082)
 *
 * Además aplica un filtro transversal que:
 *  1. Propaga la cabecera X-Usuario-Autenticado con el nombre de usuario extraído del JWT (si está autenticado),
 *     manteniendo intacta la cabecera Authorization: Bearer <token> hacia la trusted zone.
 *  2. Si el microservicio destino no responde o rechaza la conexión, traduce el fallo de red en
 *     ServicioNoDisponibleException (503 Service Unavailable) con el formato ErrorResponse de common-lib.
 */
@Configuration
public class GatewayRoutesConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutesConfig.class);

    // Cada @Bean RouterFunction es una tabla de ruteo. "actividades.url" y "reservas.url" salen de application.properties
    // (en Docker se pisan con las variables ACTIVIDADES_URL y RESERVAS_URL del docker-compose.yml).
    @Bean
    RouterFunction<ServerResponse> rutasActividades(@Value("${actividades.url}") String actividadesUrl) {
        return route("actividades-service")
                .route(path("/actividades/**", "/prestadores/**"), http(actividadesUrl))
                .filter(filtroGateway("actividades-service"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> rutasReservas(@Value("${reservas.url}") String reservasUrl) {
        return route("reservas-service")
                .route(path("/reservas/**"), http(reservasUrl))
                .filter(filtroGateway("reservas-service"))
                .build();
    }

    /**
     * Filtro de borde para cada ruta del Gateway:
     *  - Agrega trazabilidad (log + cabecera X-Usuario-Autenticado).
     *  - Convierte caídas del microservicio destino en 503 uniforme.
     */
    private HandlerFilterFunction<ServerResponse, ServerResponse> filtroGateway(String nombreServicio) {
        return (request, next) -> {
            // 1) agregamos la cabecera de identidad; 2) reenviamos al microservicio (next.handle); 3) si no responde -> 503 uniforme
            ServerRequest requestEnriquecida = propagarIdentidad(request);
            try {
                log.debug("Gateway enrutando {} {} -> {}", request.method(), request.path(), nombreServicio);
                return next.handle(requestEnriquecida);
            } catch (ResourceAccessException e) {
                // Conexión rechazada o timeout hacia el microservicio (típico: contenedor caído)
                log.warn("Microservicio {} no disponible al procesar {} {}: {}",
                        nombreServicio, request.method(), request.path(), e.getMessage());
                return respuestaNoDisponible(nombreServicio);
            } catch (Exception e) {
                // Algunas versiones envuelven el error de red en otra excepción: se recorre la cadena de causas
                if (esFalloDeConexion(e)) {
                    log.warn("Fallo de conexión hacia {} al procesar {} {}: {}",
                            nombreServicio, request.method(), request.path(), e.getMessage());
                    return respuestaNoDisponible(nombreServicio);
                }
                throw e;   // cualquier otro error NO es de red: se propaga tal cual (no lo ocultamos como 503)
            }
        };
    }

    private ServerResponse respuestaNoDisponible(String nombreServicio) {
        String mensaje = new ServicioNoDisponibleException(nombreServicio).getMessage();
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, mensaje));
    }

    private ServerRequest propagarIdentidad(ServerRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = Seguridad.usuario(auth);
        // Sin usuario real (rutas públicas, GET de catálogo) no se agrega la cabecera
        if (usuario == null || usuario.isBlank() || "anonymousUser".equals(usuario)) {
            return request;
        }
        // Información para trazabilidad/depuración: los microservicios igualmente VUELVEN a validar el JWT (defensa en profundidad)
        return ServerRequest.from(request)
                .header("X-Usuario-Autenticado", usuario)
                .build();
    }

    private boolean esFalloDeConexion(Throwable t) {
        Throwable actual = t;
        while (actual != null) {
            if (actual instanceof ConnectException || actual instanceof ResourceAccessException) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }
}
