package com.Reservas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.turismo.common.ServicioNoDisponibleException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Prueba la colaboración Reservas -> Actividades SIN levantar Actividades:
 * MockRestServiceServer simula sus respuestas HTTP (éxito, 409, 404, caída).
 * El repository se simula con Mockito, así el test no necesita base de datos.
 */
class ReservaServiceTest {

    private MockRestServiceServer actividadesFalso;
    private ReservaRepository repo;
    private ReservaService service;

    private static final String ACTIVIDAD_JSON =
            "{\"id\":1,\"nombre\":\"Trekking\",\"cupoDisponible\":5,\"latitud\":-31.4,"
            + "\"longitud\":-64.2,\"maxProbLluvia\":30,\"maxVientoKmh\":40}";

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://actividades");
        actividadesFalso = MockRestServiceServer.bindTo(builder).build();
        repo = mock(ReservaRepository.class);
        service = new ReservaService(repo, builder.build());
        when(repo.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0)); // devuelve lo recibido
    }

    private ReservaRequest pedido() {
        return new ReservaRequest(1L, "maxi", LocalDate.now().plusDays(3), 2);
    }

    private void actividadExiste() {
        actividadesFalso.expect(requestTo("http://actividades/actividades/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ACTIVIDAD_JSON, MediaType.APPLICATION_JSON));
    }

    @Test
    void reservaOk_guardaConfirmada() {
        actividadExiste();
        actividadesFalso.expect(requestTo("http://actividades/actividades/1/cupo"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withSuccess());

        Reserva r = service.reservar(pedido());

        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        verify(repo).save(any(Reserva.class));
        actividadesFalso.verify();
    }

    @Test
    void sinCupo_lanzaCupoAgotado_yNoGuarda() {
        actividadExiste();
        actividadesFalso.expect(requestTo("http://actividades/actividades/1/cupo"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT));

        assertThrows(CupoAgotadoException.class, () -> service.reservar(pedido()));
        verify(repo, never()).save(any());
    }

    @Test
    void actividadInexistente_lanza404() {
        actividadesFalso.expect(requestTo("http://actividades/actividades/1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThrows(ActividadInexistenteException.class, () -> service.reservar(pedido()));
    }

    @Test
    void actividadesCaido_lanzaServicioNoDisponible() {
        actividadesFalso.expect(requestTo("http://actividades/actividades/1"))
                .andRespond(withServerError());

        assertThrows(ServicioNoDisponibleException.class, () -> service.reservar(pedido()));
        verify(repo, never()).save(any());
    }
}
