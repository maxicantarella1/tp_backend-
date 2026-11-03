package com.turismo.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Respuestas de error de SEGURIDAD con el mismo formato JSON que el resto (ErrorResponse).
 * Las excepciones de seguridad se producen en los FILTROS de Spring Security, antes de llegar a los
 * controllers, por eso el @RestControllerAdvice no las atrapa y hay que darles su propio handler.
 *   401 = no hay token, está vencido o es inválido ("¿quién sos?")
 *   403 = hay token válido pero no alcanza el rol ("no tenés permiso")
 */
public final class ErroresSeguridad {

    private ErroresSeguridad() { }

    public static AuthenticationEntryPoint entryPoint() {
        return (req, res, ex) -> escribir(res, 401, "Unauthorized", "Falta el token de acceso o no es válido");
    }

    public static AccessDeniedHandler accessDenied() {
        return (req, res, ex) -> escribir(res, 403, "Forbidden", "No tenés permisos para esta operación");
    }

    private static void escribir(HttpServletResponse res, int status, String error, String mensaje) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"mensaje\":\""
                + mensaje + "\",\"timestamp\":\"" + LocalDateTime.now() + "\"}");
    }
}
