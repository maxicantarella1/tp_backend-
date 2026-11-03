package com.gateway;

import com.turismo.common.ManejadorErroresComun;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Punto de entrada del API GATEWAY (puerto 8080).
 *
 * RESPONSABILIDADES (según el enunciado del TPI):
 *  1. Punto único de entrada al backend: el consumidor externo no necesita conocer los puertos ni las direcciones
 *     internas de los microservicios funcionales (actividades-service en 8081 y reservas-service en 8082).
 *  2. Enrutamiento (Routing): redirige /actividades/** y /prestadores/** hacia Actividades, y /reservas/** hacia Reservas.
 *  3. Security Filter en el borde: valida el token JWT de Keycloak (firma, emisor, expiración y roles) antes de que la
 *     petición ingrese a la red interna (trusted zone). Si el token falta o el rol no alcanza, rechaza en el borde (401/403).
 *
 * @Import(ManejadorErroresComun.class) activa el formato uniforme de errores (ErrorResponse) de common-lib, por ejemplo
 * cuando un microservicio destino está caído y el Gateway responde 503 Service Unavailable.
 */
@SpringBootApplication
@Import(ManejadorErroresComun.class)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
