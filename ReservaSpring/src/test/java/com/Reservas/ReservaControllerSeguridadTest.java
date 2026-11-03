package com.Reservas;

import com.Reservas.config.ClientesConfig;
import com.Reservas.config.SecurityConfig;
import com.Reservas.controller.ReservaController;
import com.Reservas.dto.ReservaRequest;
import com.Reservas.dto.ResumenReevaluacion;
import com.Reservas.model.EstadoReserva;
import com.Reservas.model.Reserva;
import com.Reservas.service.ReservaService;
import com.Reservas.service.TokenServicio;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.turismo.common.ManejadorErroresComun;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Prueba SOLO la capa web y la seguridad de Reservas (el service se simula). jwt() inyecta un token falso
 * con usuario y roles, como si Keycloak ya hubiera autenticado: se prueba la AUTORIZACIÓN sin Keycloak.
 */
@WebMvcTest(ReservaController.class)
@Import({SecurityConfig.class, ManejadorErroresComun.class})
class ReservaControllerSeguridadTest {

    @Autowired MockMvc mvc;
    @MockBean ReservaService service;
    @MockBean TokenServicio tokenServicio;   // por si el slice carga ClientesConfig (la usa para armar el cliente HTTP)
    @MockBean JwtDecoder jwtDecoder;   // la configuración de resource server lo requiere; con jwt() no se usa

    private static RequestPostProcessor como(String usuario, String... roles) {
        return jwt().jwt(j -> j.claim("preferred_username", usuario))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList());
    }

    private static final String RESERVA = """
            {"actividadId":1,"usuario":"otro-usuario","fecha":"2099-01-10","cantidadPersonas":2,"edadMenor":20,"adultos":2}""";

    private Reserva reservaDe(String usuario) {
        Reserva r = new Reserva();
        r.setId(5L);
        r.setUsuario(usuario);
        r.setEstado(EstadoReserva.CONFIRMADA);
        return r;
    }

    @Test
    void sinToken_401_conFormatoComun() throws Exception {
        mvc.perform(get("/reservas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void turistaCrea_laReservaQuedaAnombreDelTOKEN_noDelCuerpo() throws Exception {
        when(service.reservar(any(ReservaRequest.class))).thenAnswer(inv -> reservaDe(((ReservaRequest) inv.getArgument(0)).usuario()));

        mvc.perform(post("/reservas").with(como("turista1", "TURISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value("turista1"));   // ignoró "otro-usuario"
    }

    @Test
    void adminPuedeReservarAnombreDeOtro() throws Exception {
        when(service.reservar(any(ReservaRequest.class))).thenAnswer(inv -> reservaDe(((ReservaRequest) inv.getArgument(0)).usuario()));

        mvc.perform(post("/reservas").with(como("admin1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value("otro-usuario"));
    }

    @Test
    void prestador_noPuedeReservar_403() throws Exception {
        mvc.perform(post("/reservas").with(como("prestador1", "PRESTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void turistaVeSusReservas_ynoLasAjenas() throws Exception {
        when(service.listarPorUsuario("turista1")).thenReturn(List.of(reservaDe("turista1")));

        mvc.perform(get("/reservas").with(como("turista1", "TURISTA"))).andExpect(status().isOk());

        verify(service).listarPorUsuario("turista1");
        verify(service, never()).listar();
    }

    @Test
    void adminVeTodas() throws Exception {
        when(service.listar()).thenReturn(List.of(reservaDe("a"), reservaDe("b")));
        mvc.perform(get("/reservas").with(como("admin1", "ADMIN"))).andExpect(status().isOk());
        verify(service).listar();
    }

    @Test
    void turistaVeSuReserva_200() throws Exception {
        when(service.obtener(5L)).thenReturn(reservaDe("turista1"));
        mvc.perform(get("/reservas/5").with(como("turista1", "TURISTA"))).andExpect(status().isOk());
    }

    @Test
    void turistaNoVeLaReservaDeOtro_403() throws Exception {
        when(service.obtener(5L)).thenReturn(reservaDe("turista2"));
        mvc.perform(get("/reservas/5").with(como("turista1", "TURISTA"))).andExpect(status().isForbidden());
    }

    @Test
    void turistaNoPuedeCancelarLaReservaDeOtro_yNoSeEjecutaLaCancelacion() throws Exception {
        when(service.obtener(5L)).thenReturn(reservaDe("turista2"));

        mvc.perform(post("/reservas/5/cancelar").with(como("turista1", "TURISTA"))).andExpect(status().isForbidden());

        verify(service, never()).cancelar(any());
    }

    @Test
    void turistaCancelaLaPropia() throws Exception {
        when(service.obtener(5L)).thenReturn(reservaDe("turista1"));
        when(service.cancelar(5L)).thenReturn(reservaDe("turista1"));
        mvc.perform(post("/reservas/5/cancelar").with(como("turista1", "TURISTA"))).andExpect(status().isOk());
    }

    @Test
    void reevaluarTodas_soloAdmin() throws Exception {
        when(service.reevaluarTodas()).thenReturn(new ResumenReevaluacion(3, 1, 0));

        mvc.perform(post("/reservas/reevaluacion").with(como("turista1", "TURISTA"))).andExpect(status().isForbidden());
        mvc.perform(post("/reservas/reevaluacion").with(como("admin1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisadas").value(3));
    }

    @Test
    void tokenSinRolesConocidos_403() throws Exception {
        mvc.perform(get("/reservas").with(como("alguien"))).andExpect(status().isForbidden());
    }
}
