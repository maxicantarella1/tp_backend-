package com.actividades.actividadSpring.config;

import com.turismo.common.ErroresSeguridad;
import com.turismo.common.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SEGURIDAD del microservicio de Actividades: es un RESOURCE SERVER de OAuth2/OpenID Connect.
 * No autentica usuarios (eso lo hace Keycloak): solo VERIFICA el JWT que llega en
 *   Authorization: Bearer <token>
 * (firma, emisor y vencimiento) y arma los permisos a partir de los roles del token.
 *
 * DOS niveles de autorización:
 *  1) Acá, por URL: qué es público y qué exige estar autenticado.
 *  2) En los controllers, con @PreAuthorize: qué ROL necesita cada operación.
 *  3) En el service: reglas de PROPIEDAD (un prestador solo toca lo suyo).
 *
 * @EnableMethodSecurity activa @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            // API sin sesión ni cookies: la identidad viaja en el token en cada petición -> CSRF no aplica
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Documentación OpenAPI/Swagger: pública
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // El catálogo (consultas) es PÚBLICO: un turista puede ver actividades antes de registrarse
                .requestMatchers(HttpMethod.GET, "/actividades/**", "/prestadores/**").permitAll()
                // Todo lo demás exige token válido; el rol exacto lo decide @PreAuthorize
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter()))
                .authenticationEntryPoint(ErroresSeguridad.entryPoint())    // 401 con nuestro formato
                .accessDeniedHandler(ErroresSeguridad.accessDenied()));      // 403 con nuestro formato
        return http.build();
    }
}
