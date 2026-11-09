package com.Reservas;

import com.turismo.common.ErroresSeguridad;
import com.turismo.common.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SEGURIDAD del microservicio de Reservas: RESOURCE SERVER OAuth2/OIDC (igual que Actividades).
 * Verifica el JWT de Keycloak (firma, emisor, vencimiento) y arma los permisos con los roles del token.
 * Acá NADA es público salvo la documentación: reservar implica una identidad.
 * Qué rol necesita cada operación -> @PreAuthorize en el controller. De quién es cada reserva -> controller.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())     // sin sesión ni cookies: el token viaja en cada petición
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter()))
                .authenticationEntryPoint(ErroresSeguridad.entryPoint())
                .accessDeniedHandler(ErroresSeguridad.accessDenied()));
        return http.build();
    }
}
