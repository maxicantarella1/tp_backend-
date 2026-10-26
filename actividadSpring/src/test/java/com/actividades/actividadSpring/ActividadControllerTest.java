package com.actividades.actividadSpring;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la API: levantan la app con H2 y simulan peticiones HTTP con MockMvc (sin abrir puerto).
 * Verifican también que el manejador de errores de common-lib responde con el formato uniforme.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActividadControllerTest {

    @Autowired
    MockMvc mvc;

    /** JSON de una actividad válida con cupo 5. */
    private static final String ACTIVIDAD = """
            {"nombre":"Trekking","latitud":-31.4,"longitud":-64.2,"cupoDisponible":5,
             "precio":45000,"maxProbLluvia":30,"maxVientoKmh":40}""";

    /** Crea una actividad y devuelve su id leyéndolo del JSON de respuesta. */
    private long crearActividad() throws Exception {
        String json = mvc.perform(post("/actividades").contentType(MediaType.APPLICATION_JSON).content(ACTIVIDAD))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void crearYConsultar() throws Exception {
        long id = crearActividad();
        mvc.perform(get("/actividades/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Trekking"));
    }

    @Test
    void actividadInexistente_404_conFormatoComun() throws Exception {
        mvc.perform(get("/actividades/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void datosInvalidos_400() throws Exception {
        mvc.perform(post("/actividades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"cupoDisponible\":-1,\"precio\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void descontarCupo_ok_y_luegoConflicto() throws Exception {
        long id = crearActividad();   // cupo 5
        mvc.perform(patch("/actividades/" + id + "/cupo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupoDisponible").value(2));
        // quedan 2 y se piden 3 -> 409
        mvc.perform(patch("/actividades/" + id + "/cupo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":3}"))
                .andExpect(status().isConflict());
    }
}
