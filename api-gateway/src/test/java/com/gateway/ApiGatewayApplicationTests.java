package com.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas integrales del API Gateway + Security Filter.
 *
 * Verifican:
 *  1. Que las rutas protegidas rechacen peticiones anónimas con 401 (y formato ErrorResponse de common-lib).
 *  2. Que el Security Filter del Gateway valide los roles de Keycloak antes de enrutar (403 si el rol no alcanza).
 *  3. Que el catálogo público (GET /actividades, GET /prestadores) no exija token en el borde.
 *  4. Que si el microservicio destino no está levantado, el Gateway responda 503 Service Unavailable controlado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Puertos locales cerrados para verificar tanto el filtro de seguridad como la respuesta 503 cuando pasa el filtro
        "actividades.url=http://localhost:59991",
        "reservas.url=http://localhost:59992"
})
/**
 * Pruebas del API Gateway: 401 sin token (formato común), 403 por rol rechazado en el borde ANTES de enrutar, catálogo público que pasa el filtro, enrutado a Reservas con token de turista y 503 uniforme cuando el microservicio destino está caído.
 */
class ApiGatewayApplicationTests {

    @Autowired
    private MockMvc mvc;

    /** Simula un JWT emitido por Keycloak con el usuario y rol indicados. */
    private static RequestPostProcessor tokenComo(String usuario, String rol) {
        return jwt()
                .jwt(j -> j.claim("preferred_username", usuario)
                           .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
    }

    @Test
    void contextoLevantaCorrectamente() {
        // Verifica que el contexto de Spring Boot + Spring Cloud Gateway + Security levante sin errores
    }

    @Test
    void reservasSinToken_devuelve401ConFormatoComun() throws Exception {
        mvc.perform(get("/reservas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void crearActividadSinToken_devuelve401() throws Exception {
        mvc.perform(post("/actividades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void turistaIntentaCrearActividad_gatewayRechazaCon403AntesDeEnrutar() throws Exception {
        mvc.perform(post("/actividades")
                        .with(tokenComo("turista1", "turista"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void turistaIntentaModificarCupoDirecto_gatewayRechazaCon403() throws Exception {
        mvc.perform(patch("/actividades/1/cupo")
                        .with(tokenComo("turista1", "turista"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"2026-11-10\",\"cantidad\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void prestadorIntentaCrearReserva_gatewayRechazaCon403() throws Exception {
        mvc.perform(post("/reservas")
                        .with(tokenComo("prestador1", "prestador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void turistaIntentaDispararReevaluacionGlobal_gatewayRechazaCon403() throws Exception {
        mvc.perform(post("/reservas/reevaluacion")
                        .with(tokenComo("turista1", "turista")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void catalogoPublicoSinToken_pasaElFiltroYDevuelve503SiActividadesEstaCaido() throws Exception {
        // Demuestra dos cosas a la vez:
        // 1) GET /actividades NO pide token (pasa el Security Filter del Gateway).
        // 2) Al intentar enrutar al puerto 59991 (caído), el Gateway captura el fallo y responde 503 uniforme.
        mvc.perform(get("/actividades"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void reservaConTokenDeTurista_pasaElFiltroYEnrutaAReservas() throws Exception {
        // Con rol TURISTA pasa el Security Filter del Gateway y llega al enrutador (que devuelve 503 porque 59992 está apagado)
        mvc.perform(get("/reservas")
                        .with(tokenComo("turista1", "turista")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
