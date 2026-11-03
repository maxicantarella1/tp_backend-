package com.turismo.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** Utilidades para saber QUIÉN está haciendo la petición y qué rol tiene (se usan en los controllers). */
public final class Seguridad {

    private Seguridad() { }

    /** Nombre de usuario del token (claim preferred_username de Keycloak); si no está, el "sub". */
    public static String usuario(Authentication auth) {
        if (auth == null) return null;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("preferred_username");
            return u != null ? u : jwt.getSubject();
        }
        return auth.getName();
    }

    /** ¿Tiene el rol? Se pasa sin prefijo: tieneRol(auth, "admin"). */
    public static boolean tieneRol(Authentication auth, String rol) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + rol.toUpperCase()));
    }
}
