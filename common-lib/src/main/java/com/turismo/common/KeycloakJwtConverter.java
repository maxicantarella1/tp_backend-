package com.turismo.common;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Traduce un JWT emitido por KEYCLOAK a la "autenticación" de Spring Security.
 *
 * POR QUÉ HACE FALTA: Spring Security, por defecto, saca los permisos del claim "scope". Keycloak, en cambio,
 * guarda los ROLES DE REALM en  "realm_access": { "roles": ["turista", ...] }.  Sin este conversor,
 * hasRole('ADMIN') nunca se cumpliría. Cada rol "admin" se convierte en la autoridad "ROLE_ADMIN".
 *
 * Está en common-lib porque TODOS los componentes (microservicios y, más adelante, el Gateway) necesitan
 * interpretar el token exactamente igual.
 */
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> autoridades = rolesDe(jwt).stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))
                .toList();
        String nombre = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, autoridades, nombre != null ? nombre : jwt.getSubject());
    }

    /** Lee realm_access.roles del token. Si el claim no existe o tiene otro formato, devuelve lista vacía. */
    static List<String> rolesDe(Jwt jwt) {
        Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
        if (realm == null || !(realm.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream().map(String::valueOf).toList();
    }
}
