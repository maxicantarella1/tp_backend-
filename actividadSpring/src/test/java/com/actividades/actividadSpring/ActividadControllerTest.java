package com.actividades.actividadSpring;

import com.actividades.actividadSpring.client.GeocodingClient;
import com.actividades.actividadSpring.dto.Coordenadas;
import com.actividades.actividadSpring.model.Actividad;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.turismo.common.ServicioNoDisponibleException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pruebas de la API con H2 y MockMvc (sin abrir puerto). Dos cosas se SIMULAN:
 *  - la API de geocodificación (@MockBean), para no depender de Internet;
 *  - el login de Keycloak: jwt() inyecta un token falso con el usuario y los roles indicados, como si
 *    Keycloak ya hubiera autenticado. Así se prueba la AUTORIZACIÓN sin levantar Keycloak.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActividadControllerTest {

    @Autowired MockMvc mvc;
    @MockBean GeocodingClient geocoding;

    /** Simula un token de Keycloak: usuario (preferred_username) + roles de realm (ej: "PRESTADOR"). */
    private static RequestPostProcessor como(String usuario, String... roles) {
        return jwt().jwt(j -> j.claim("preferred_username", usuario))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList());
    }

    private String extraerId(String json) {
        return json.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    /** Crea un prestador cuyo dueño es `usuario` (rol prestador) y devuelve su id. */
    private String crearPrestador(String usuario) throws Exception {
        String json = mvc.perform(post("/prestadores").with(como(usuario, "PRESTADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sierras Aventura\",\"contacto\":\"info@sierras.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value(usuario))
                .andReturn().getResponse().getContentAsString();
        return extraerId(json);
    }

    /** Actividad válida; ubicación y cupos parametrizables. */
    private String actividad(String prestadorId, String ubicacion, int cupoMax, int cupoMin) {
        return """
            {"nombre":"Trekking","prestadorId":%s,%s,"cupoMaximo":%d,"cupoMinimo":%d,"precio":45000,
             "horaInicio":"09:00","duracionHoras":6,"edadMinima":12,
             "temporadaDesdeMes":10,"temporadaHastaMes":3,"maxProbLluvia":30,"maxVientoKmh":40}"""
                .formatted(prestadorId, ubicacion, cupoMax, cupoMin);
    }

    private static final String COORDS = "\"latitud\":-31.4,\"longitud\":-64.2";

    /** Crea una actividad como el prestador `dueno` (dueño del prestador) y devuelve su id. */
    private String crearActividad(String dueno, int cupoMax) throws Exception {
        String json = mvc.perform(post("/actividades").with(como(dueno, "PRESTADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(crearPrestador(dueno), COORDS, cupoMax, 0)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extraerId(json);
    }

    // ============ reglas de negocio de actividades ============

    @Test
    void prestadorCreaActividadConCoordenadas_nace_activa() throws Exception {
        mvc.perform(post("/actividades").with(como("prestador1", "PRESTADOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(crearPrestador("prestador1"), COORDS, 5, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void crearConLocalidad_completaCoordenadasConLaApiExterna() throws Exception {
        when(geocoding.buscar(anyString())).thenReturn(new Coordenadas(-31.98, -64.56));
        mvc.perform(post("/actividades").with(como("prestador1", "PRESTADOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(crearPrestador("prestador1"), "\"localidad\":\"Villa General Belgrano\"", 5, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitud").value(-31.98));
    }

    @Test
    void sinUbicacion_400() throws Exception {
        mvc.perform(post("/actividades").with(como("prestador1", "PRESTADOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(crearPrestador("prestador1"), "\"otro\":\"x\"", 5, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void geocodingCaido_503() throws Exception {
        when(geocoding.buscar(anyString())).thenThrow(new ServicioNoDisponibleException("geocoding"));
        mvc.perform(post("/actividades").with(como("prestador1", "PRESTADOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(crearPrestador("prestador1"), "\"localidad\":\"Cosquín\"", 5, 0)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void cupoMinimoMayorAlMaximo_400() throws Exception {
        mvc.perform(post("/actividades").with(como("prestador1", "PRESTADOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(crearPrestador("prestador1"), COORDS, 5, 9)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void prestadorInexistente_404() throws Exception {
        mvc.perform(post("/actividades").with(como("admin1", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad("99999", COORDS, 5, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void datosInvalidos_400() throws Exception {
        mvc.perform(post("/actividades").with(como("admin1", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"cupoMaximo\":0,\"precio\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actividadInexistente_404_conFormatoComun() throws Exception {
        mvc.perform(get("/actividades/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ============ cupo por fecha ============

    private static final String SERVICIO = "reservas-service";

    @Test
    void cupoSeControlaPorFecha() throws Exception {
        String id = crearActividad("prestador1", 5);
        String f1 = "{\"fecha\":\"2026-11-10\",\"cantidad\":3}";
        String f2 = "{\"fecha\":\"2026-11-11\",\"cantidad\":3}";
        mvc.perform(patch("/actividades/" + id + "/cupo").with(como(SERVICIO, "SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content(f1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.libres").value(2));
        // otro día tiene su propio cupo
        mvc.perform(patch("/actividades/" + id + "/cupo").with(como(SERVICIO, "SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content(f2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.libres").value(2));
        // pedir 3 más el día 10 (quedan 2) -> 409
        mvc.perform(patch("/actividades/" + id + "/cupo").with(como(SERVICIO, "SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content(f1))
                .andExpect(status().isConflict());
    }

    @Test
    void devolverCupo_liberaLugares() throws Exception {
        String id = crearActividad("prestador1", 5);
        String body = "{\"fecha\":\"2026-11-10\",\"cantidad\":4}";
        mvc.perform(patch("/actividades/" + id + "/cupo").with(como(SERVICIO, "SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(patch("/actividades/" + id + "/cupo/devolucion").with(como(SERVICIO, "SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.libres").value(5));
        mvc.perform(get("/actividades/" + id + "/disponibilidad?fecha=2026-11-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ocupados").value(0));
    }

    @Test
    void actividadDadaDeBaja_noAdmiteReservas() throws Exception {
        String id = crearActividad("prestador1", 5);
        mvc.perform(delete("/actividades/" + id).with(como("prestador1", "PRESTADOR"))).andExpect(status().isNoContent());
        mvc.perform(get("/actividades/" + id)).andExpect(jsonPath("$.activa").value(false));
        mvc.perform(patch("/actividades/" + id + "/cupo").with(como(SERVICIO, "SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"2026-11-10\",\"cantidad\":1}"))
                .andExpect(status().isConflict());
    }

    // ============ SEGURIDAD: autenticación y autorización ============

    @Test
    void catalogoEsPublico_sinToken() throws Exception {
        mvc.perform(get("/actividades")).andExpect(status().isOk());
        mvc.perform(get("/prestadores")).andExpect(status().isOk());
    }

    @Test
    void sinToken_enOperacionProtegida_401_conFormatoComun() throws Exception {
        mvc.perform(post("/prestadores").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void turista_noPuedeCrearActividades_403() throws Exception {
        mvc.perform(post("/actividades").with(como("turista1", "TURISTA")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad("1", COORDS, 5, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void prestador_noPuedeCrearActividadesEnElPrestadorDeOtro_403() throws Exception {
        String prestadorDeUno = crearPrestador("prestador1");
        mvc.perform(post("/actividades").with(como("prestador2", "PRESTADOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(prestadorDeUno, COORDS, 5, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_puedeCrearActividadesEnCualquierPrestador() throws Exception {
        String prestadorDeUno = crearPrestador("prestador1");
        mvc.perform(post("/actividades").with(como("admin1", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(actividad(prestadorDeUno, COORDS, 5, 0)))
                .andExpect(status().isCreated());
    }

    @Test
    void prestador_noPuedeDarDeBajaActividadAjena_403() throws Exception {
        String id = crearActividad("prestador1", 5);
        mvc.perform(delete("/actividades/" + id).with(como("prestador2", "PRESTADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void turista_noPuedeTocarElCupo_403() throws Exception {
        String id = crearActividad("prestador1", 5);
        mvc.perform(patch("/actividades/" + id + "/cupo").with(como("turista1", "TURISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"2026-11-10\",\"cantidad\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinToken_noPuedeTocarElCupo_401() throws Exception {
        mvc.perform(patch("/actividades/1/cupo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"2026-11-10\",\"cantidad\":1}"))
                .andExpect(status().isUnauthorized());
    }
}
