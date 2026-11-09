package com.gateway.config;

import com.turismo.common.ErroresSeguridad;
import com.turismo.common.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SECURITY FILTER del API Gateway (primera línea de defensa en el borde).
 *
 * Reutiliza exactamente los mismos componentes de common-lib que usan los microservicios:
 *  - KeycloakJwtConverter: traduce realm_access.roles del JWT a ROLE_ADMIN, ROLE_PRESTADOR, ROLE_TURISTA, ROLE_SERVICIO.
 *  - ErroresSeguridad: devuelve 401 y 403 con el mismo JSON (ErrorResponse) que el resto del sistema.
 *
 * ESTRATEGIA DE TRUSTED ZONE Y DEFENSA EN PROFUNDIDAD:
 *  - El Gateway filtra en el borde las peticiones sin token (401) o sin el rol requerido para la ruta (403),
 *    evitando tráfico innecesario hacia los microservicios internos.
 *  - Cuando la petición es válida, el Gateway la enruta conservando la cabecera Authorization: Bearer <JWT> y
 *    agregando X-Usuario-Autenticado, para que el microservicio destino vuelva a validar el token y aplique las
 *    reglas de propiedad del dominio (ej. "este prestador es mío", "esta reserva es mía").
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 1) Catálogo público: cualquiera puede consultar actividades, prestadores y disponibilidad
                .requestMatchers(HttpMethod.GET, "/actividades/**", "/prestadores/**").permitAll()

                // 2) Gestión de prestadores y actividades: solo PRESTADOR o ADMIN
                .requestMatchers(HttpMethod.POST, "/prestadores/**", "/actividades/**").hasAnyRole("PRESTADOR", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/actividades/**").hasAnyRole("PRESTADOR", "ADMIN")

                // 3) Descuento/devolución de cupo: operación interna reservada a la cuenta de SERVICIO o ADMIN
                .requestMatchers(HttpMethod.PATCH, "/actividades/**").hasAnyRole("SERVICIO", "ADMIN")

                // 4) Reevaluación masiva: operación administrativa exclusiva de ADMIN
                .requestMatchers(HttpMethod.POST, "/reservas/reevaluacion").hasRole("ADMIN")

                // 5) Resto de operaciones de reservas: TURISTA o ADMIN
                .requestMatchers("/reservas/**").hasAnyRole("TURISTA", "ADMIN")

                // Cualquier otra ruta exige estar autenticado
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter()))
                .authenticationEntryPoint(ErroresSeguridad.entryPoint())
                .accessDeniedHandler(ErroresSeguridad.accessDenied()));

        return http.build();
    }
}
